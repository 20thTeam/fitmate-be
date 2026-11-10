package com.team20.fitmate.realtime.room;

import com.team20.fitmate.realtime.message.ConnectionPayload;
import com.team20.fitmate.realtime.message.ErrorPayload;
import com.team20.fitmate.realtime.message.LeftPayload;
import com.team20.fitmate.realtime.message.LeftPayload.LeaveReason;
import com.team20.fitmate.realtime.message.ParticipantView;
import com.team20.fitmate.realtime.message.PosePayload;
import com.team20.fitmate.realtime.message.RealtimeErrorCode;
import com.team20.fitmate.realtime.message.RoomSnapshotPayload;
import com.team20.fitmate.realtime.message.ServerMessage;
import com.team20.fitmate.realtime.message.ServerMessageType;
import com.team20.fitmate.realtime.message.StatusPayload;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 운동방별 참가자 상태와 연결을 메모리에서 관리하고, 같은 방에 이벤트를 전달한다.
 * <p>
 * 지키는 규칙:
 * <ul>
 *     <li>내용을 해석하지 않고 전달만 한다. 운동 기록(DB)은 만들거나 바꾸지 않는다(이슈 #7 합의).</li>
 *     <li>다른 참가자의 상태로 내 진행 상태를 바꾸는 경로가 없다. 진행 상태는 본인 STATUS로만 바뀐다.</li>
 *     <li>연결이 끊기면 바로 퇴장시키지 않고 연결 불안정으로 표시한 뒤 재접속 대기 시간 동안 자리를 남긴다.</li>
 *     <li>정원은 DB가 아니라 이 레지스트리의 현재 참가자 수(연결 불안정 포함)로 판단한다.</li>
 * </ul>
 * 모든 상태 변경은 하나의 잠금 안에서 일어난다. 시연 규모(방 하나, 5명 안팎)에서는 이 단순함이 더 중요하다.
 * 메시지 전송은 연결마다 {@code ConcurrentWebSocketSessionDecorator}로 감싸 두어 잠금 안에서 보내도 오래 막히지 않는다.
 */
@Slf4j
@Component
public class RoomSessionRegistry {

    /**
     * 응답 대기 시간 동안 메시지가 없어 닫을 때 쓰는 닫힘 상태(코드 4500).
     * <p>
     * Spring의 {@code SESSION_NOT_RELIABLE}은 기본 사유가 "전송 시간 초과" 문구라서 원인을 헷갈리게 한다.
     * 코드는 그대로 두고 사유만 바꿔, 프론트가 사유로 응답 없음 끊김을 구분할 수 있게 한다.
     */
    static final CloseStatus HEARTBEAT_TIMEOUT = CloseStatus.SESSION_NOT_RELIABLE.withReason("HEARTBEAT_TIMEOUT");

    private final RealtimeProperties properties;
    private final JsonMapper jsonMapper;
    private final Clock clock;

    /** 모든 상태 변경을 묶는 잠금 */
    private final Object lock = new Object();

    /** 연결 식별값 → 연결. JOIN 전 연결도 여기 있다 */
    private final Map<String, Connection> connections = new HashMap<>();

    /** 운동방 식별값 → 방 상태. 참가자가 한 명도 없으면 지운다 */
    private final Map<Long, RoomState> rooms = new HashMap<>();

    public RoomSessionRegistry(RealtimeProperties properties, JsonMapper jsonMapper, Clock clock) {
        this.properties = properties;
        this.jsonMapper = jsonMapper;
        this.clock = clock;
    }

    /**
     * 새 연결을 등록한다. JOIN 전까지는 어느 방에도 속하지 않는다.
     *
     * @param session 동시 전송에 안전하도록 감싼 연결
     */
    public void connect(WebSocketSession session) {
        synchronized (lock) {
            Instant now = clock.instant();
            connections.put(session.getId(), new Connection(session, now));
        }
    }

    /**
     * 이 연결이 이미 운동방에 입장했는지 확인한다.
     */
    public boolean isJoined(String sessionId) {
        synchronized (lock) {
            Connection connection = connections.get(sessionId);
            return connection != null && connection.isJoined();
        }
    }

    /**
     * DB 확인을 마친 참가자를 운동방에 넣는다.
     * <ul>
     *     <li>처음 입장: 정원을 확인하고 넣는다. 본인에게 ROOM_SNAPSHOT, 다른 참가자에게 PARTICIPANT_JOINED.</li>
     *     <li>이미 방에 있는 참가자(재접속·다른 창): 진행 상태를 유지한 채 새 연결로 바꾼다. 예전 연결은 SESSION_REPLACED로 닫는다.
     *     연결 불안정이었다면 다른 참가자에게 CONNECTION_STATUS(CONNECTED).</li>
     * </ul>
     */
    public void join(String sessionId, JoinTicket ticket) {
        synchronized (lock) {
            Connection connection = connections.get(sessionId);
            if (connection == null) {
                return;
            }
            if (connection.isJoined()) {
                sendError(connection, RealtimeErrorCode.ALREADY_JOINED);
                return;
            }
            Instant now = clock.instant();
            connection.lastSeenAt = now;

            RoomState room = rooms.computeIfAbsent(ticket.roomId(), id -> new RoomState(id, ticket.capacity()));
            RoomPresence existing = room.presences.get(ticket.participantId());
            if (existing != null) {
                rejoin(room, existing, connection, now);
                return;
            }
            if (room.presences.size() >= room.capacity) {
                connections.remove(sessionId);
                sendErrorAndClose(connection, RealtimeErrorCode.ROOM_FULL);
                removeIfEmpty(room);
                return;
            }

            RoomPresence presence = new RoomPresence(ticket, sessionId);
            room.presences.put(presence.getParticipantId(), presence);
            connection.bind(room.roomId, presence.getParticipantId());
            sendSnapshot(connection, room, now);
            broadcastExcept(room, presence.getParticipantId(),
                    message(ServerMessageType.PARTICIPANT_JOINED, room.roomId, presence.getParticipantId(), now, presence.toView()));
        }
    }

    /**
     * 이미 방에 있는 참가자가 새 연결로 다시 들어온 경우를 처리한다.
     */
    private void rejoin(RoomState room, RoomPresence presence, Connection connection, Instant now) {
        boolean wasUnstable = presence.getConnectionStatus() == ConnectionStatus.UNSTABLE;
        String oldSessionId = presence.getSessionId();
        if (oldSessionId != null) {
            Connection old = connections.remove(oldSessionId);
            if (old != null) {
                sendErrorAndClose(old, RealtimeErrorCode.SESSION_REPLACED);
            }
        }
        presence.reconnect(connection.session.getId());
        connection.bind(room.roomId, presence.getParticipantId());
        sendSnapshot(connection, room, now);
        if (wasUnstable) {
            broadcastExcept(room, presence.getParticipantId(),
                    message(ServerMessageType.CONNECTION_STATUS, room.roomId, presence.getParticipantId(), now,
                            new ConnectionPayload(ConnectionStatus.CONNECTED)));
        }
    }

    /**
     * 본인의 현재 운동·진행 상태를 바꾸고 같은 방의 다른 참가자에게 PARTICIPANT_STATUS로 전달한다.
     */
    public void updateStatus(String sessionId, StatusPayload status) {
        synchronized (lock) {
            Connection connection = connections.get(sessionId);
            if (connection == null) {
                return;
            }
            Instant now = clock.instant();
            connection.lastSeenAt = now;
            if (!connection.isJoined()) {
                sendError(connection, RealtimeErrorCode.JOIN_REQUIRED);
                return;
            }
            RoomState room = rooms.get(connection.roomId);
            RoomPresence presence = room.presences.get(connection.participantId);
            presence.updateStatus(status);
            broadcastExcept(room, presence.getParticipantId(),
                    message(ServerMessageType.PARTICIPANT_STATUS, room.roomId, presence.getParticipantId(), now, presence.toStatus()));
        }
    }

    /**
     * 본인의 동작 정보를 같은 방의 다른 참가자에게 PARTICIPANT_POSE로 전달한다(보낸 사람 제외).
     * <p>
     * <b>동작 정보는 저장하지 않는다.</b> DB는 물론 이 레지스트리의 참가자 상태에도 남기지 않고, 전달한 뒤 버린다.
     * 그래서 재접속하거나 새로 입장한 참가자는 ROOM_SNAPSHOT으로 다른 참가자의 자세를 받지 않고, 다음 POSE부터 받는다.
     * 인식이 끊기면 클라이언트가 POSE를 보내지 않으므로 서버도 아무것도 만들어 보내지 않는다(임의 움직임 없음).
     */
    public void relayPose(String sessionId, PosePayload pose) {
        synchronized (lock) {
            Connection connection = connections.get(sessionId);
            if (connection == null) {
                return;
            }
            Instant now = clock.instant();
            connection.lastSeenAt = now;
            if (!connection.isJoined()) {
                sendError(connection, RealtimeErrorCode.JOIN_REQUIRED);
                return;
            }
            RoomState room = rooms.get(connection.roomId);
            RoomPresence presence = room.presences.get(connection.participantId);
            broadcastExcept(room, presence.getParticipantId(),
                    message(ServerMessageType.PARTICIPANT_POSE, room.roomId, presence.getParticipantId(), now, pose));
        }
    }

    /**
     * 연결 유지 확인. 마지막 메시지 시각을 갱신하고 PONG으로 답한다. JOIN 전에도 보낼 수 있다.
     */
    public void ping(String sessionId) {
        synchronized (lock) {
            Connection connection = connections.get(sessionId);
            if (connection == null) {
                return;
            }
            Instant now = clock.instant();
            connection.lastSeenAt = now;
            send(connection, message(ServerMessageType.PONG, connection.roomId, connection.participantId, now, null));
        }
    }

    /**
     * 직접 퇴장. 방에서 바로 빼고 다른 참가자에게 PARTICIPANT_LEFT(LEFT)를 보낸 뒤 연결을 닫는다.
     */
    public void leave(String sessionId) {
        synchronized (lock) {
            Connection connection = connections.remove(sessionId);
            if (connection == null) {
                return;
            }
            if (connection.isJoined()) {
                RoomState room = rooms.get(connection.roomId);
                room.presences.remove(connection.participantId);
                broadcastExcept(room, connection.participantId,
                        message(ServerMessageType.PARTICIPANT_LEFT, room.roomId, connection.participantId, clock.instant(),
                                new LeftPayload(LeaveReason.LEFT)));
                removeIfEmpty(room);
            }
            close(connection, CloseStatus.NORMAL);
        }
    }

    /**
     * 연결이 닫혔다(네트워크 끊김, 브라우저 종료 등). 서버가 일부러 닫은 연결은 이미 지워져 있어 아무것도 하지 않는다.
     * <p>
     * 입장한 참가자였다면 퇴장시키지 않고 연결 불안정으로 표시해 재접속을 기다린다.
     */
    public void disconnected(String sessionId) {
        synchronized (lock) {
            Connection connection = connections.remove(sessionId);
            if (connection == null || !connection.isJoined()) {
                return;
            }
            markUnstable(connection, clock.instant());
        }
    }

    /**
     * 이 연결에 ERROR를 보낸다. 연결을 닫는 오류면 보낸 뒤 닫는다.
     */
    public void sendError(String sessionId, RealtimeErrorCode errorCode) {
        synchronized (lock) {
            Connection connection = connections.get(sessionId);
            if (connection == null) {
                return;
            }
            if (errorCode.isClosesConnection()) {
                connections.remove(sessionId);
                if (connection.isJoined()) {
                    markUnstable(connection, clock.instant());
                }
                sendErrorAndClose(connection, errorCode);
            } else {
                sendError(connection, errorCode);
            }
        }
    }

    /**
     * 시간이 지난 연결과 참가자를 정리한다. 스케줄러가 주기적으로 부른다.
     * <ul>
     *     <li>JOIN 대기 시간이 지난 연결: JOIN_TIMEOUT으로 닫는다.</li>
     *     <li>마지막 메시지 후 응답 대기 시간이 지난 참가자: 연결 불안정으로 표시하고 연결을 닫는다.</li>
     *     <li>재접속 대기 시간이 지난 연결 불안정 참가자: 퇴장시키고 PARTICIPANT_LEFT(TIMEOUT)를 보낸다.</li>
     * </ul>
     */
    public void sweep() {
        synchronized (lock) {
            Instant now = clock.instant();
            for (Connection connection : new ArrayList<>(connections.values())) {
                if (!connection.isJoined()) {
                    if (!now.isBefore(connection.connectedAt.plus(properties.joinTimeout()))) {
                        connections.remove(connection.session.getId());
                        sendErrorAndClose(connection, RealtimeErrorCode.JOIN_TIMEOUT);
                    }
                } else if (!now.isBefore(connection.lastSeenAt.plus(properties.heartbeatTimeout()))) {
                    connections.remove(connection.session.getId());
                    markUnstable(connection, now);
                    close(connection, HEARTBEAT_TIMEOUT);
                }
            }
            for (RoomState room : new ArrayList<>(rooms.values())) {
                for (RoomPresence presence : new ArrayList<>(room.presences.values())) {
                    if (presence.isReconnectExpired(now, properties.reconnectGrace())) {
                        room.presences.remove(presence.getParticipantId());
                        broadcastExcept(room, presence.getParticipantId(),
                                message(ServerMessageType.PARTICIPANT_LEFT, room.roomId, presence.getParticipantId(), now,
                                        new LeftPayload(LeaveReason.TIMEOUT)));
                    }
                }
                removeIfEmpty(room);
            }
        }
    }

    /**
     * 운동방의 현재 참가자 목록(입장 순서). 점검·테스트용이다.
     */
    public List<ParticipantView> participants(Long roomId) {
        synchronized (lock) {
            RoomState room = rooms.get(roomId);
            return room == null ? List.of() : room.views();
        }
    }

    /**
     * 모든 연결을 닫고 상태를 비운다. 테스트 사이 초기화용이다.
     */
    public void clear() {
        synchronized (lock) {
            List<Connection> all = new ArrayList<>(connections.values());
            connections.clear();
            rooms.clear();
            all.forEach(connection -> close(connection, CloseStatus.GOING_AWAY));
        }
    }

    // ===== 내부 처리 =====

    /** 입장한 연결의 참가자를 연결 불안정으로 바꾸고 다른 참가자에게 알린다(이미 다른 연결로 바뀌었으면 무시) */
    private void markUnstable(Connection connection, Instant now) {
        RoomState room = rooms.get(connection.roomId);
        if (room == null) {
            return;
        }
        RoomPresence presence = room.presences.get(connection.participantId);
        if (presence == null || !connection.session.getId().equals(presence.getSessionId())) {
            return;
        }
        presence.markUnstable(now);
        broadcastExcept(room, presence.getParticipantId(),
                message(ServerMessageType.CONNECTION_STATUS, room.roomId, presence.getParticipantId(), now,
                        new ConnectionPayload(ConnectionStatus.UNSTABLE)));
    }

    /** 본인에게 방 전체 상태를 보낸다 */
    private void sendSnapshot(Connection connection, RoomState room, Instant now) {
        send(connection, message(ServerMessageType.ROOM_SNAPSHOT, room.roomId, connection.participantId, now,
                new RoomSnapshotPayload(room.capacity, room.views())));
    }

    /** 같은 방에서 지금 연결된 다른 참가자 모두에게 보낸다 */
    private void broadcastExcept(RoomState room, Long excludedParticipantId, ServerMessage message) {
        String json = serialize(message);
        for (RoomPresence presence : room.presences.values()) {
            if (presence.getParticipantId().equals(excludedParticipantId) || presence.getSessionId() == null) {
                continue;
            }
            Connection target = connections.get(presence.getSessionId());
            if (target != null) {
                sendRaw(target, json);
            }
        }
    }

    /** ERROR를 보낸다(연결은 닫지 않음) */
    private void sendError(Connection connection, RealtimeErrorCode errorCode) {
        send(connection, message(ServerMessageType.ERROR, connection.roomId, connection.participantId, clock.instant(),
                ErrorPayload.from(errorCode)));
    }

    /** ERROR를 보내고 연결을 닫는다. 호출 전에 connections에서 지워 두어야 닫힘 콜백이 무시된다 */
    private void sendErrorAndClose(Connection connection, RealtimeErrorCode errorCode) {
        sendError(connection, errorCode);
        close(connection, CloseStatus.POLICY_VIOLATION.withReason(errorCode.name()));
    }

    /** 방에 참가자가 없으면 지운다 */
    private void removeIfEmpty(RoomState room) {
        if (room.presences.isEmpty()) {
            rooms.remove(room.roomId);
        }
    }

    /** 공통 봉투를 만든다 */
    private ServerMessage message(ServerMessageType type, Long roomId, Long participantId, Instant now, Object payload) {
        return new ServerMessage(type, roomId, participantId, now.toString(), payload);
    }

    /** 한 연결로 보낸다 */
    private void send(Connection connection, ServerMessage message) {
        sendRaw(connection, serialize(message));
    }

    /** 직렬화한 메시지를 보낸다. 실패해도 다른 참가자 처리는 계속한다(닫힌 연결은 닫힘 콜백이 정리한다) */
    private void sendRaw(Connection connection, String json) {
        try {
            connection.session.sendMessage(new TextMessage(json));
        } catch (IOException | RuntimeException e) {
            log.debug("실시간 메시지 전송 실패 sessionId={}", connection.session.getId(), e);
        }
    }

    /** 메시지를 JSON으로 바꾼다 */
    private String serialize(ServerMessage message) {
        return jsonMapper.writeValueAsString(message);
    }

    /** 연결을 닫는다. 이미 닫혔으면 무시한다 */
    private void close(Connection connection, CloseStatus status) {
        try {
            if (connection.session.isOpen()) {
                connection.session.close(status);
            }
        } catch (IOException | RuntimeException e) {
            log.debug("실시간 연결 닫기 실패 sessionId={}", connection.session.getId(), e);
        }
    }

    /**
     * 연결 하나. JOIN 전에는 roomId·participantId가 null이다.
     */
    private static final class Connection {

        /** 동시 전송에 안전하도록 감싼 연결 */
        private final WebSocketSession session;

        /** 연결된 시각(JOIN 대기 시간 기준) */
        private final Instant connectedAt;

        /** 마지막으로 메시지를 받은 시각(응답 대기 시간 기준) */
        private Instant lastSeenAt;

        /** 입장한 운동방. JOIN 전이면 null */
        private Long roomId;

        /** 입장한 참가자. JOIN 전이면 null */
        private Long participantId;

        private Connection(WebSocketSession session, Instant connectedAt) {
            this.session = session;
            this.connectedAt = connectedAt;
            this.lastSeenAt = connectedAt;
        }

        private boolean isJoined() {
            return roomId != null;
        }

        private void bind(Long roomId, Long participantId) {
            this.roomId = roomId;
            this.participantId = participantId;
        }
    }

    /**
     * 운동방 하나의 상태.
     */
    private static final class RoomState {

        /** 운동방 식별값 */
        private final Long roomId;

        /** 정원. 방 상태를 처음 만들 때의 DB 값이다 */
        private final int capacity;

        /** 참가자 식별값 → 상태. 입장 순서를 유지한다 */
        private final Map<Long, RoomPresence> presences = new LinkedHashMap<>();

        private RoomState(Long roomId, int capacity) {
            this.roomId = roomId;
            this.capacity = capacity;
        }

        private List<ParticipantView> views() {
            return presences.values().stream().map(RoomPresence::toView).toList();
        }
    }
}
