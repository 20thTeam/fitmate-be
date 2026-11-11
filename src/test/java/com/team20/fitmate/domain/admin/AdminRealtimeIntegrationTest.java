package com.team20.fitmate.domain.admin;

import com.team20.fitmate.domain.admin.auth.AdminKeyInterceptor;
import com.team20.fitmate.domain.participant.dto.ParticipantCreateRequest;
import com.team20.fitmate.domain.participant.dto.ParticipantCreateResponse;
import com.team20.fitmate.domain.participant.service.ParticipantService;
import com.team20.fitmate.domain.room.service.RoomService;
import com.team20.fitmate.realtime.room.RoomSessionRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 실제 서버와 실제 WebSocket 연결로 운영 API의 실시간 연동을 확인한다.
 * <ul>
 *     <li>방 접속자 현황의 {@code connected}가 실제 연결 여부와 맞는지</li>
 *     <li>초기화하면 열린 실시간 연결이 닫히는지</li>
 * </ul>
 * 초기화가 실제로 커밋되므로 다른 테스트와 DB를 나누지 않게 설정값을 달리 줘서 별도 컨텍스트로 띄운다.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "admin.key=" + AdminRealtimeIntegrationTest.ADMIN_KEY)
class AdminRealtimeIntegrationTest {

    static final String ADMIN_KEY = "realtime-test-admin-key";
    private static final JsonMapper MAPPER = JsonMapper.builder().build();
    private static final long WAIT_SECONDS = 5;

    @LocalServerPort
    private int port;

    @Autowired
    private ParticipantService participantService;

    @Autowired
    private RoomService roomService;

    @Autowired
    private RoomSessionRegistry registry;

    private final HttpClient http = HttpClient.newHttpClient();
    private final List<WebSocketSession> sessions = new ArrayList<>();

    @AfterEach
    void tearDown() {
        sessions.forEach(session -> {
            try {
                session.close();
            } catch (Exception ignored) {
                // 이미 닫힌 연결
            }
        });
        registry.clear();
    }

    /** 운영 키를 붙여 운영 API를 부르고 응답 본문을 JSON으로 읽는다 */
    private JsonNode callAdmin(String method, String path) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .header(AdminKeyInterceptor.ADMIN_KEY_HEADER, ADMIN_KEY)
                .method(method, HttpRequest.BodyPublishers.noBody())
                .build();
        HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).isEqualTo(200);
        return MAPPER.readTree(response.body());
    }

    /** 방 접속자 현황에서 참가자의 connected 값 */
    private boolean connectedOf(Long roomId, Long participantId) throws Exception {
        for (JsonNode room : callAdmin("GET", "/api/admin/rooms").path("data")) {
            if (room.path("roomId").asLong() != roomId) {
                continue;
            }
            for (JsonNode participant : room.path("participants")) {
                if (participant.path("participantId").asLong() == participantId) {
                    return participant.path("connected").asBoolean();
                }
            }
        }
        throw new AssertionError("방 접속자 현황에 참가자가 없습니다: " + participantId);
    }

    @Test
    @DisplayName("웹소켓으로 입장한 참가자만 connected=true이고, 초기화하면 실시간 연결이 닫힌다")
    void connectedAndResetClosesConnections() throws Exception {
        Long roomId = roomService.getAll().getFirst().roomId();
        ParticipantCreateResponse online = participantService.create(new ParticipantCreateRequest("접속", "CHARACTER_01"));
        ParticipantCreateResponse offline = participantService.create(new ParticipantCreateRequest("미접속", "CHARACTER_02"));
        roomService.enter(online.clientToken(), roomId);
        roomService.enter(offline.clientToken(), roomId);

        // 웹소켓 연결 후 JOIN, 스냅샷을 받을 때까지 기다린다
        CompletableFuture<String> snapshot = new CompletableFuture<>();
        CompletableFuture<CloseStatus> closed = new CompletableFuture<>();
        WebSocketSession session = new StandardWebSocketClient().execute(new TextWebSocketHandler() {
            @Override
            protected void handleTextMessage(WebSocketSession s, TextMessage message) {
                snapshot.complete(message.getPayload());
            }

            @Override
            public void afterConnectionClosed(WebSocketSession s, CloseStatus status) {
                closed.complete(status);
            }
        }, "ws://localhost:" + port + "/ws").get(WAIT_SECONDS, TimeUnit.SECONDS);
        sessions.add(session);
        session.sendMessage(new TextMessage("{\"type\":\"JOIN\",\"payload\":{\"token\":\""
                + online.clientToken() + "\",\"roomId\":" + roomId + "}}"));
        assertThat(MAPPER.readTree(snapshot.get(WAIT_SECONDS, TimeUnit.SECONDS)).path("type").asString())
                .isEqualTo("ROOM_SNAPSHOT");

        assertThat(connectedOf(roomId, online.participantId())).isTrue();
        assertThat(connectedOf(roomId, offline.participantId())).isFalse();

        JsonNode reset = callAdmin("POST", "/api/admin/reset").path("data");
        assertThat(reset.path("deletedParticipants").asInt()).isGreaterThanOrEqualTo(2);

        assertThat(closed.get(WAIT_SECONDS, TimeUnit.SECONDS)).isNotNull();
        assertThat(registry.participants(roomId)).isEmpty();
        assertThat(callAdmin("GET", "/api/admin/rooms").path("data").get(0).path("participants")).isEmpty();
    }
}
