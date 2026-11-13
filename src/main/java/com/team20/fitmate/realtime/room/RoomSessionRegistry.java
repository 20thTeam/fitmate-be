package com.team20.fitmate.realtime.room;

import com.team20.fitmate.domain.cheer.dto.CheerRecordCommand;
import com.team20.fitmate.domain.party.service.PublicPartyOccupancy;
import com.team20.fitmate.realtime.message.CheerPayload;
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
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 운동방별 참가자 상태와 연결을 메모리에서 관리하고, 같은 방에 이벤트를 전달한다.
 * <p>
 * 지키는 규칙:
 * <ul>
 *     <li>내용을 해석하지 않고 전달만 한다. 운동 기록(DB)은 만들거나 바꾸지 않는다(이슈 #7 합의).</li>
 *     <li>다른 참가자의 상태로 내 진행 상태를 바꾸는 경로가 없다. 진행 상태는 본인 STATUS로만 바뀐다.</li>
 *     <li>연결이 끊기면 바로 퇴장시키지 않고 연결 불안정으로 표시한 뒤 재접속 대기 시간 동안 자리를 남긴다.</li>
 *     <li>정원은 DB가 아니라 이 레지스트리의 현재 참가자 수(연결 불안정 포함)로 판단한다.</li>
 *     <li>친구 파티 운동방과 공개 파티(이슈 #11)는 같은 경로를 쓴다. 공개 파티는 정원이 없고, 응원은 전달만 하고 저장하지 않는다.</li>
 *     <li>챌린지 판(이슈 #34)도 같은 경로를 쓴다. 정원은 판의 maxPlayers이고, 판이 끝나면 {@link #closeContestGame(Long)}으로 정리한다.</li>
 * </ul>
 * 모든 상태 변경은 하나의 잠금 안에서 일어난다. 시연 규모(방 하나, 5명 안팎)에서는 이 단순함이 더 중요하다.
 * 메시지 전송은 연결마다 {@code ConcurrentWebSocketSessionDecorator}로 감싸 두어 잠금 안에서 보내도 오래 막히지 않는다.
 */
@Slf4j
@Component
public class RoomSessionRegistry implements PublicPartyOccupancy {

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

    /** 끝난 챌린지 판의 연결을 닫을 때 쓰는 닫힘 상태(정상 종료 1000, 사유 CONTEST_GAME_FINISHED) */
    static final CloseStatus CONTEST_GAME_FINISHED = CloseStatus.NORMAL.withReason("CONTEST_GAME_FINISHED");

    /** 실시간 방(운동방·공개 파티·챌린지 판) → 방 상태. 참가자가 한 명도 없으면 지운다 */
    private final Map<SpaceKey, RoomState> rooms = new HashMap<>();

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

            RoomState room = rooms.computeIfAbsent(ticket.space(), space -> new RoomState(space, ticket.capacity()));
            RoomPresence existing = room.presences.get(ticket.participantId());
            if (existing != null) {
                rejoin(room, existing, connection, now);
                return;
            }
            if (room.isFull()) {
                connections.remove(sessionId);
                sendErrorAndClose(connection, RealtimeErrorCode.ROOM_FULL);
                removeIfEmpty(room);
                return;
            }

            RoomPresence presence = new RoomPresence(ticket, sessionId);
            room.presences.put(presence.getParticipantId(), presence);
            connection.bind(room.space, presence.getParticipantId());
            sendSnapshot(connection, room, now, false);
            broadcastExcept(room, presence.getParticipantId(),
                    message(ServerMessageType.PARTICIPANT_JOINED, room.space, presence.getParticipantId(), now, presence.toView()));
        }
    }

    /**
     * 이미 방에 있는 참가자가 새 연결로 다시 들어온 경우를 처리한다(이슈 #7 재접속).
     * <ul>
     *     <li>진행 상태·현재 운동은 끊기기 전 마지막 값 그대로 둔다. 끊긴 동안의 상태·동작 정보는 만들어 넣지 않는다.</li>
     *     <li>본인에게 {@code reconnected=true}인 ROOM_SNAPSHOT을 보내 복원된 입장임을 알린다.</li>
     *     <li>예전 연결이 아직 열려 있으면(다른 창) 먼저 닫고 지운다. 그 연결로 늦게 온 메시지는 더 이상 전달되지 않는다.</li>
     * </ul>
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
        connection.bind(room.space, presence.getParticipantId());
        sendSnapshot(connection, room, now, true);
        if (wasUnstable) {
            broadcastExcept(room, presence.getParticipantId(),
                    message(ServerMessageType.CONNECTION_STATUS, room.space, presence.getParticipantId(), now,
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
            RoomState room = rooms.get(connection.space);
            RoomPresence presence = room.presences.get(connection.participantId);
            presence.updateStatus(status);
            broadcastExcept(room, presence.getParticipantId(),
                    message(ServerMessageType.PARTICIPANT_STATUS, room.space, presence.getParticipantId(), now, presence.toStatus()));
        }
    }

    /**
     * 본인의 동작 정보를 같은 방의 다른 참가자에게 PARTICIPANT_POSE로 전달한다(보낸 사람 제외).
     * <p>
     * <b>동작 정보는 저장하지 않는다.</b> DB는 물론 이 레지스트리의 참가자 상태에도 남기지 않고, 전달한 뒤 버린다.
     * 그래서 재접속하거나 새로 입장한 참가자는 ROOM_SNAPSHOT으로 다른 참가자의 자세를 받지 않고, 다음 POSE부터 받는다.
     * 인식이 끊기면 클라이언트가 POSE를 보내지 않으므로 서버도 아무것도 만들어 보내지 않는다(임의 움직임 없음).
     * <p>
     * 참가자 한 명이 1초에 {@code realtime.pose-max-per-second}개보다 많이 보내면 넘는 메시지는 조용히 버린다.
     * 한 명의 과도한 전송이 방 전체의 전송 버퍼를 채우지 않게 하려는 것이다. 버려도 연결 유지 시각은 갱신한다.
     * <p>
     * 진행 상태가 CAMERA_ERROR(카메라 권한 거부·장치 오류)인 참가자의 동작 정보는 중계하지 않고 조용히 버린다.
     * 버린 메시지는 빈도 상한 계산에도 넣지 않는다.
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
            RoomState room = rooms.get(connection.space);
            RoomPresence presence = room.presences.get(connection.participantId);
            if (!presence.canSharePose() || !presence.tryAcquirePoseSlot(now, properties.poseMaxPerSecond())) {
                return;
            }
            broadcastExcept(room, presence.getParticipantId(),
                    message(ServerMessageType.PARTICIPANT_POSE, room.space, presence.getParticipantId(), now, pose));
        }
    }

    /**
     * 같은 방 친구 한 명에게 응원을 보낸다(1:1). 다른 참가자에게는 보내지 않는다.
     * <ul>
     *     <li>받은 사람: CHEER_RECEIVED (participantId = 보낸 사람)</li>
     *     <li>보낸 사람: CHEER_SENT (같은 내용). 화면에서 응원 대상을 확인하는 데 쓴다</li>
     *     <li>대상이 같은 방에 없거나(퇴장·다른 방) 연결 불안정: 보낸 사람에게 CHEER_TARGET_UNAVAILABLE. 연결은 유지한다</li>
     *     <li>자기 자신을 대상으로 하면 INVALID_MESSAGE</li>
     *     <li>같은 대상에게 {@code realtime.cheer-cooldown} 안에 다시 보내면 연타로 보고 오류 없이 무시한다</li>
     * </ul>
     * 대상이 없어 실패한 응원은 연타 제한 시간에 넣지 않는다.
     * <p>
     * DB 저장은 여기서 하지 않는다(잠금 안에서 DB를 기다리지 않게). 전달에 성공하면 저장할 내용을 돌려주고,
     * 호출한 쪽이 잠금 밖에서 {@code cheer} 도메인으로 넘긴다.
     *
     * @return 대상에게 전달했으면 저장할 응원, 전달하지 않았으면(실패·연타) 또는 공개 파티 응원이면 비어 있음
     */
    public Optional<CheerRecordCommand> cheer(String sessionId, CheerPayload cheer) {
        synchronized (lock) {
            Connection connection = connections.get(sessionId);
            if (connection == null) {
                return Optional.empty();
            }
            Instant now = clock.instant();
            connection.lastSeenAt = now;
            if (!connection.isJoined()) {
                sendError(connection, RealtimeErrorCode.JOIN_REQUIRED);
                return Optional.empty();
            }
            if (cheer.targetParticipantId().equals(connection.participantId)) {
                sendError(connection, RealtimeErrorCode.INVALID_MESSAGE);
                return Optional.empty();
            }
            RoomState room = rooms.get(connection.space);
            Connection target = connectedConnectionOf(room, cheer.targetParticipantId());
            if (target == null) {
                sendError(connection, RealtimeErrorCode.CHEER_TARGET_UNAVAILABLE);
                return Optional.empty();
            }
            RoomPresence sender = room.presences.get(connection.participantId);
            if (!sender.tryAcquireCheerSlot(cheer.targetParticipantId(), now, properties.cheerCooldown())) {
                return Optional.empty();
            }
            send(target, message(ServerMessageType.CHEER_RECEIVED, room.space, connection.participantId, now, cheer));
            send(connection, message(ServerMessageType.CHEER_SENT, room.space, connection.participantId, now, cheer));
            if (!room.space.isRoom()) {
                // 공개 파티·챌린지 판 응원은 전달만 한다. 응원 저장(cheer 테이블)은 친구 파티 운동방 기준이다
                return Optional.empty();
            }
            return Optional.of(new CheerRecordCommand(
                    room.space.id(), connection.participantId, cheer.targetParticipantId(), cheer.reaction(), now));
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
            send(connection, message(ServerMessageType.PONG, connection.space, connection.participantId, now, null));
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
                RoomState room = rooms.get(connection.space);
                room.presences.remove(connection.participantId);
                broadcastExcept(room, connection.participantId,
                        message(ServerMessageType.PARTICIPANT_LEFT, room.space, connection.participantId, clock.instant(),
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
                                message(ServerMessageType.PARTICIPANT_LEFT, room.space, presence.getParticipantId(), now,
                                        new LeftPayload(LeaveReason.TIMEOUT)));
                    }
                }
                removeIfEmpty(room);
            }
        }
    }

    /**
     * 친구 파티 운동방의 현재 참가자 목록(입장 순서). 점검·테스트용이다.
     */
    public List<ParticipantView> participants(Long roomId) {
        return participants(SpaceKey.room(roomId));
    }

    /**
     * 실시간 방의 현재 참가자 목록(입장 순서). 점검·테스트용이다.
     */
    public List<ParticipantView> participants(SpaceKey space) {
        synchronized (lock) {
            RoomState room = rooms.get(space);
            return room == null ? List.of() : room.views();
        }
    }

    /**
     * 공개 파티의 현재 참가자 수(연결 불안정 포함). 홈 카드 조회 API가 쓴다.
     */
    @Override
    public int countParticipants(Long partyId) {
        synchronized (lock) {
            RoomState room = rooms.get(SpaceKey.publicParty(partyId));
            return room == null ? 0 : room.presences.size();
        }
    }

    /**
     * 지금 누군가 들어와 있는 챌린지 판 식별값 목록. 끝난 판을 정리하는 스케줄러가 쓴다.
     */
    public Set<Long> activeContestGameIds() {
        synchronized (lock) {
            return rooms.keySet().stream()
                    .filter(SpaceKey::isContestGame)
                    .map(SpaceKey::id)
                    .collect(Collectors.toSet());
        }
    }

    /**
     * 끝난 챌린지 판의 실시간 방을 정리한다. 남은 참가자를 모두 빼고 연결을 정상 종료(1000, CONTEST_GAME_FINISHED)로 닫는다.
     * <p>
     * 판이 끝난 것은 모두가 아는 일이라 PARTICIPANT_LEFT·ERROR는 보내지 않는다. 결과는 프론트가 REST로 조회한다.
     */
    public void closeContestGame(Long gameId) {
        synchronized (lock) {
            RoomState room = rooms.remove(SpaceKey.contestGame(gameId));
            if (room == null) {
                return;
            }
            for (RoomPresence presence : room.presences.values()) {
                if (presence.getSessionId() == null) {
                    continue;
                }
                Connection connection = connections.remove(presence.getSessionId());
                if (connection != null) {
                    close(connection, CONTEST_GAME_FINISHED);
                }
            }
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
        RoomState room = rooms.get(connection.space);
        if (room == null) {
            return;
        }
        RoomPresence presence = room.presences.get(connection.participantId);
        if (presence == null || !connection.session.getId().equals(presence.getSessionId())) {
            return;
        }
        presence.markUnstable(now);
        broadcastExcept(room, presence.getParticipantId(),
                message(ServerMessageType.CONNECTION_STATUS, room.space, presence.getParticipantId(), now,
                        new ConnectionPayload(ConnectionStatus.UNSTABLE)));
    }

    /** 같은 방에서 지금 연결된 참가자의 연결을 찾는다. 방에 없거나 연결 불안정이면 null */
    private Connection connectedConnectionOf(RoomState room, Long participantId) {
        RoomPresence presence = room.presences.get(participantId);
        if (presence == null || presence.getConnectionStatus() != ConnectionStatus.CONNECTED
                || presence.getSessionId() == null) {
            return null;
        }
        return connections.get(presence.getSessionId());
    }

    /**
     * 본인에게 방 전체 상태를 보낸다.
     *
     * @param reconnected 이미 있던 자리로 돌아온 입장(재접속·새 창)이면 true
     */
    private void sendSnapshot(Connection connection, RoomState room, Instant now, boolean reconnected) {
        send(connection, message(ServerMessageType.ROOM_SNAPSHOT, room.space, connection.participantId, now,
                new RoomSnapshotPayload(room.capacity, room.views(), reconnected)));
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
        send(connection, message(ServerMessageType.ERROR, connection.space, connection.participantId, clock.instant(),
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
            rooms.remove(room.space);
        }
    }

    /**
     * 공통 봉투를 만든다. 친구 파티 운동방이면 roomId, 공개 파티면 partyId, 챌린지 판이면 contestGameId를 채운다.
     * 방이 정해지기 전이면 셋 다 null이다.
     */
    private ServerMessage message(ServerMessageType type, SpaceKey space, Long participantId, Instant now, Object payload) {
        Long roomId = space != null && space.isRoom() ? space.id() : null;
        Long partyId = space != null && space.isPublicParty() ? space.id() : null;
        Long contestGameId = space != null && space.isContestGame() ? space.id() : null;
        return new ServerMessage(type, roomId, partyId, contestGameId, participantId, now.toString(), payload);
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
     * 연결 하나. JOIN 전에는 space·participantId가 null이다.
     */
    private static final class Connection {

        /** 동시 전송에 안전하도록 감싼 연결 */
        private final WebSocketSession session;

        /** 연결된 시각(JOIN 대기 시간 기준) */
        private final Instant connectedAt;

        /** 마지막으로 메시지를 받은 시각(응답 대기 시간 기준) */
        private Instant lastSeenAt;

        /** 입장한 실시간 방(운동방 또는 공개 파티). JOIN 전이면 null */
        private SpaceKey space;

        /** 입장한 참가자. JOIN 전이면 null */
        private Long participantId;

        private Connection(WebSocketSession session, Instant connectedAt) {
            this.session = session;
            this.connectedAt = connectedAt;
            this.lastSeenAt = connectedAt;
        }

        private boolean isJoined() {
            return space != null;
        }

        private void bind(SpaceKey space, Long participantId) {
            this.space = space;
            this.participantId = participantId;
        }
    }

    /**
     * 실시간 방 하나(운동방 또는 공개 파티)의 상태.
     */
    private static final class RoomState {

        /** 실시간 방 식별값 */
        private final SpaceKey space;

        /** 정원. 운동방은 방 상태를 처음 만들 때의 DB 값, 공개 파티는 정원이 없어 null */
        private final Integer capacity;

        /** 참가자 식별값 → 상태. 입장 순서를 유지한다 */
        private final Map<Long, RoomPresence> presences = new LinkedHashMap<>();

        private RoomState(SpaceKey space, Integer capacity) {
            this.space = space;
            this.capacity = capacity;
        }

        /** 정원이 있고 다 찼는지. 정원이 없으면(공개 파티) 언제나 false */
        private boolean isFull() {
            return capacity != null && presences.size() >= capacity;
        }

        private List<ParticipantView> views() {
            return presences.values().stream().map(RoomPresence::toView).toList();
        }
    }
}
