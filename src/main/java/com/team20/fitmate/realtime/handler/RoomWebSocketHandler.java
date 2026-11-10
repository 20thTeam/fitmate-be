package com.team20.fitmate.realtime.handler;

import com.team20.fitmate.domain.cheer.dto.CheerRecordCommand;
import com.team20.fitmate.domain.cheer.service.CheerRecorder;
import com.team20.fitmate.realtime.message.CheerPayload;
import com.team20.fitmate.realtime.message.ClientMessageType;
import com.team20.fitmate.realtime.message.JoinPayload;
import com.team20.fitmate.realtime.message.PosePayload;
import com.team20.fitmate.realtime.message.RealtimeErrorCode;
import com.team20.fitmate.realtime.message.RealtimeException;
import com.team20.fitmate.realtime.message.StatusPayload;
import com.team20.fitmate.realtime.room.JoinTicket;
import com.team20.fitmate.realtime.room.JoinValidator;
import com.team20.fitmate.realtime.room.RealtimeProperties;
import com.team20.fitmate.realtime.room.RoomSessionRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.ConcurrentWebSocketSessionDecorator;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * 운동방 실시간 연결({@code /ws})의 메시지를 받아 {@link RoomSessionRegistry}로 넘기는 핸들러.
 * <p>
 * 여기서는 JSON을 읽고 형식을 검사하는 일만 한다. 방 상태와 전달은 레지스트리가, DB 확인은 {@link JoinValidator}가 맡는다.
 * 클라이언트가 보낸 roomId·participantId는 JOIN 때만 쓰고, 이후 메시지는 연결에 묶인 참가자로만 처리한다
 * (다른 참가자인 척하는 메시지를 막기 위해서다).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RoomWebSocketHandler extends TextWebSocketHandler {

    private final RoomSessionRegistry registry;
    private final JoinValidator joinValidator;
    private final RealtimeProperties properties;
    private final JsonMapper jsonMapper;

    /** 응원 저장 구현체. 데이터 담당이 만들기 전에는 없을 수 있다 */
    private final ObjectProvider<CheerRecorder> cheerRecorder;

    /**
     * 연결이 맺어지면 동시 전송에 안전하도록 감싸서 등록한다. JOIN 전까지는 어느 방에도 속하지 않는다.
     */
    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        WebSocketSession concurrentSession = new ConcurrentWebSocketSessionDecorator(
                session, (int) properties.sendTimeLimit().toMillis(), properties.sendBufferSize());
        registry.connect(concurrentSession);
    }

    /**
     * 메시지 종류에 따라 처리한다. 형식 오류는 INVALID_MESSAGE, 처리 못 한 오류는 INTERNAL_ERROR로 알린다.
     */
    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        String sessionId = session.getId();
        try {
            JsonNode root = jsonMapper.readTree(message.getPayload());
            ClientMessageType type = ClientMessageType.valueOf(root.path("type").asString());
            JsonNode payload = root.path("payload");
            switch (type) {
                case JOIN -> handleJoin(sessionId, payload);
                case STATUS -> handleStatus(sessionId, payload);
                case POSE -> handlePose(sessionId, message, payload);
                case CHEER -> handleCheer(sessionId, payload);
                case PING -> registry.ping(sessionId);
                case LEAVE -> registry.leave(sessionId);
            }
        } catch (RealtimeException e) {
            registry.sendError(sessionId, e.getErrorCode());
        } catch (JacksonException | IllegalArgumentException e) {
            registry.sendError(sessionId, RealtimeErrorCode.INVALID_MESSAGE);
        } catch (RuntimeException e) {
            log.error("실시간 메시지 처리 실패 sessionId={}", sessionId, e);
            registry.sendError(sessionId, RealtimeErrorCode.INTERNAL_ERROR);
        }
    }

    /**
     * JOIN: 형식을 검사하고 DB로 참가자·운동방·입장 여부를 확인한 뒤 방에 넣는다.
     * DB 확인은 레지스트리 잠금 밖에서 한다.
     */
    private void handleJoin(String sessionId, JsonNode payload) {
        if (registry.isJoined(sessionId)) {
            throw new RealtimeException(RealtimeErrorCode.ALREADY_JOINED);
        }
        JoinPayload join = jsonMapper.treeToValue(payload, JoinPayload.class);
        if (join == null || !join.hasValidFormat()) {
            throw new RealtimeException(RealtimeErrorCode.INVALID_MESSAGE);
        }
        JoinTicket ticket = joinValidator.validate(join.token(), join.roomId());
        registry.join(sessionId, ticket);
    }

    /**
     * STATUS: 형식을 검사하고 같은 방에 전달한다.
     */
    private void handleStatus(String sessionId, JsonNode payload) {
        StatusPayload status = jsonMapper.treeToValue(payload, StatusPayload.class);
        if (status == null || !status.hasValidFormat()) {
            throw new RealtimeException(RealtimeErrorCode.INVALID_MESSAGE);
        }
        registry.updateStatus(sessionId, status);
    }

    /**
     * POSE: 크기와 관절 배열 모양만 검사하고 같은 방에 전달한다. 좌표는 해석하지 않고 저장하지도 않는다.
     * <p>
     * 크기 상한({@code realtime.pose-max-bytes})을 넘으면 INVALID_MESSAGE로 거절한다.
     * 동작 정보는 자주 오므로 큰 메시지 하나가 방 전체 전송을 막지 않게 하려는 것이다.
     */
    private void handlePose(String sessionId, TextMessage message, JsonNode payload) {
        if (message.getPayloadLength() > properties.poseMaxBytes()) {
            throw new RealtimeException(RealtimeErrorCode.INVALID_MESSAGE);
        }
        PosePayload pose = PosePayload.from(payload);
        if (pose == null) {
            throw new RealtimeException(RealtimeErrorCode.INVALID_MESSAGE);
        }
        registry.relayPose(sessionId, pose);
    }

    /**
     * CHEER: 대상·리액션 형식을 검사하고 대상 한 명에게만 전달한다.
     * 전달에 성공한 응원만 레지스트리 잠금 밖에서 {@code cheer} 도메인으로 넘겨 저장한다.
     */
    private void handleCheer(String sessionId, JsonNode payload) {
        CheerPayload cheer = jsonMapper.treeToValue(payload, CheerPayload.class);
        if (cheer == null || !cheer.hasValidFormat()) {
            throw new RealtimeException(RealtimeErrorCode.INVALID_MESSAGE);
        }
        registry.cheer(sessionId, cheer).ifPresent(this::recordCheer);
    }

    /**
     * 전달된 응원을 저장 구현체({@link CheerRecorder})로 넘긴다.
     * <p>
     * 응원은 이미 대상에게 전달됐으므로 저장에 실패해도 취소하거나 보낸 사람에게 오류를 보내지 않고 로그만 남긴다.
     * 저장 구현체가 아직 없으면(데이터 담당 작업 전) 저장하지 않고 로그만 남긴다.
     */
    private void recordCheer(CheerRecordCommand command) {
        CheerRecorder recorder = cheerRecorder.getIfAvailable();
        if (recorder == null) {
            log.info("응원 저장 구현체가 없어 저장하지 않음 roomId={} senderId={} receiverId={} reaction={}",
                    command.roomId(), command.senderId(), command.receiverId(), command.reaction());
            return;
        }
        try {
            recorder.record(command);
        } catch (RuntimeException e) {
            log.warn("응원 저장 실패(전달은 완료) roomId={} senderId={} receiverId={}",
                    command.roomId(), command.senderId(), command.receiverId(), e);
        }
    }

    /**
     * 전송 오류는 로그만 남긴다. 연결 정리는 뒤따르는 닫힘 콜백에서 한다.
     */
    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        log.debug("실시간 전송 오류 sessionId={}", session.getId(), exception);
    }

    /**
     * 연결이 닫히면 레지스트리에 알린다. 입장한 참가자는 퇴장이 아니라 연결 불안정이 된다.
     */
    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        registry.disconnected(session.getId());
    }
}
