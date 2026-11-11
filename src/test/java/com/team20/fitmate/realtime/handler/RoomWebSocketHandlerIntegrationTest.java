package com.team20.fitmate.realtime.handler;

import com.team20.fitmate.domain.cheer.dto.CheerRecordCommand;
import com.team20.fitmate.domain.cheer.service.CheerRecorder;
import com.team20.fitmate.domain.participant.dto.ParticipantCreateRequest;
import com.team20.fitmate.domain.participant.dto.ParticipantCreateResponse;
import com.team20.fitmate.domain.participant.service.ParticipantService;
import com.team20.fitmate.domain.room.service.RoomService;
import com.team20.fitmate.realtime.room.ConnectionStatus;
import com.team20.fitmate.realtime.room.RoomSessionRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

/**
 * 실제 서버를 무작위 포트로 띄우고 실제 WebSocket 클라이언트로 {@code /ws}에 붙어 운동방 흐름을 확인한다.
 * 참가자 발급과 REST 입장은 서비스로 미리 해 둔다.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(RoomWebSocketHandlerIntegrationTest.RecordingCheerRecorder.class)
class RoomWebSocketHandlerIntegrationTest {

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

    private Long roomId;
    private final List<TestClient> clients = new ArrayList<>();

    @BeforeEach
    void setUp() {
        roomId = roomService.getAll().getFirst().roomId();
        RecordingCheerRecorder.RECORDED.clear();
    }

    @AfterEach
    void tearDown() {
        clients.forEach(TestClient::closeQuietly);
        registry.clear();
    }

    /** 참가자를 발급하고 지정 운동방에 REST 입장까지 마친다 */
    private ParticipantCreateResponse enteredParticipant(String nickname) {
        ParticipantCreateResponse participant = participantService.create(new ParticipantCreateRequest(nickname, "CHARACTER_01"));
        roomService.enter(participant.clientToken(), roomId);
        return participant;
    }

    /** 연결하고 JOIN까지 보낸 뒤 스냅샷을 받을 때까지 기다린다 */
    private TestClient joinedClient(ParticipantCreateResponse participant) throws Exception {
        TestClient client = connect(null);
        client.send(join(participant.clientToken(), roomId));
        assertThat(client.next().path("type").asString()).isEqualTo("ROOM_SNAPSHOT");
        return client;
    }

    private TestClient connect(String origin) throws Exception {
        TestClient client = TestClient.connect(URI.create("ws://localhost:" + port + "/ws"), origin);
        clients.add(client);
        return client;
    }

    private static String join(String token, Long roomId) {
        return "{\"type\":\"JOIN\",\"payload\":{\"token\":\"" + token + "\",\"roomId\":" + roomId + "}}";
    }

    @Test
    @DisplayName("두 참가자가 입장하면 서로의 닉네임·캐릭터·상태를 같은 값으로 본다")
    void twoParticipantsSeeEachOther() throws Exception {
        ParticipantCreateResponse juyoung = enteredParticipant("주영");
        ParticipantCreateResponse jisu = enteredParticipant("지수");
        TestClient first = joinedClient(juyoung);

        TestClient second = connect(null);
        second.send(join(jisu.clientToken(), roomId));

        JsonNode snapshot = second.next();
        assertThat(snapshot.path("type").asString()).isEqualTo("ROOM_SNAPSHOT");
        assertThat(snapshot.path("roomId").asLong()).isEqualTo(roomId);
        JsonNode participants = snapshot.path("payload").path("participants");
        assertThat(participants).hasSize(2);
        assertThat(participants.get(0).path("participantId").asLong()).isEqualTo(juyoung.participantId());
        assertThat(participants.get(0).path("nickname").asString()).isEqualTo("주영");
        assertThat(participants.get(1).path("participantId").asLong()).isEqualTo(jisu.participantId());

        JsonNode joined = first.next();
        assertThat(joined.path("type").asString()).isEqualTo("PARTICIPANT_JOINED");
        assertThat(joined.path("participantId").asLong()).isEqualTo(jisu.participantId());
        assertThat(joined.path("payload").path("nickname").asString()).isEqualTo("지수");
        assertThat(joined.path("payload").path("characterCode").asString()).isEqualTo("CHARACTER_01");
        assertThat(joined.path("sentAt").asString()).isNotBlank();
    }

    @Test
    @DisplayName("보낸 메시지 어디에도 재방문 토큰이 들어가지 않는다")
    void tokenNeverSent() throws Exception {
        ParticipantCreateResponse juyoung = enteredParticipant("주영");
        ParticipantCreateResponse jisu = enteredParticipant("지수");
        TestClient first = joinedClient(juyoung);
        TestClient second = connect(null);
        second.send(join(jisu.clientToken(), roomId));

        String snapshot = second.nextRaw();
        String joined = first.nextRaw();

        assertThat(snapshot).doesNotContain(juyoung.clientToken(), jisu.clientToken());
        assertThat(joined).doesNotContain(juyoung.clientToken(), jisu.clientToken());
    }

    @Test
    @DisplayName("STATUS를 보내면 다른 참가자 화면에 현재 운동과 진행 상태가 전달된다")
    void statusRelayed() throws Exception {
        ParticipantCreateResponse juyoung = enteredParticipant("주영");
        ParticipantCreateResponse jisu = enteredParticipant("지수");
        TestClient first = joinedClient(juyoung);
        TestClient second = joinedClient(jisu);
        first.next(); // 지수 입장 알림

        first.send("{\"type\":\"STATUS\",\"payload\":{\"exerciseId\":3,\"exerciseName\":\"스쿼트\",\"progressStatus\":\"EXERCISING\"}}");

        JsonNode status = second.next();
        assertThat(status.path("type").asString()).isEqualTo("PARTICIPANT_STATUS");
        assertThat(status.path("participantId").asLong()).isEqualTo(juyoung.participantId());
        assertThat(status.path("payload").path("exerciseId").asLong()).isEqualTo(3);
        assertThat(status.path("payload").path("exerciseName").asString()).isEqualTo("스쿼트");
        assertThat(status.path("payload").path("progressStatus").asString()).isEqualTo("EXERCISING");
        assertThat(status.path("payload").propertyNames())
                .as("문서에 정한 세 필드만 나가야 한다")
                .containsExactlyInAnyOrder("exerciseId", "exerciseName", "progressStatus");
    }

    @Test
    @DisplayName("형식이 틀린 메시지는 INVALID_MESSAGE를 받고 연결은 유지된다")
    void invalidMessages() throws Exception {
        TestClient client = joinedClient(enteredParticipant("주영"));

        client.send("not json");
        assertThat(client.next().path("payload").path("code").asString()).isEqualTo("INVALID_MESSAGE");
        client.send("{\"type\":\"DANCE\"}");
        assertThat(client.next().path("payload").path("code").asString()).isEqualTo("INVALID_MESSAGE");
        client.send("{\"type\":\"STATUS\",\"payload\":{\"exerciseName\":\"스쿼트\"}}");
        JsonNode error = client.next();
        assertThat(error.path("payload").path("code").asString()).isEqualTo("INVALID_MESSAGE");
        assertThat(error.path("payload").path("closed").asBoolean()).isFalse();

        client.send("{\"type\":\"PING\"}");
        assertThat(client.next().path("type").asString()).isEqualTo("PONG");
    }

    @Test
    @DisplayName("JOIN 전에 STATUS를 보내면 JOIN_REQUIRED를 받는다")
    void joinRequired() throws Exception {
        TestClient client = connect(null);

        client.send("{\"type\":\"STATUS\",\"payload\":{\"progressStatus\":\"WAITING\"}}");

        assertThat(client.next().path("payload").path("code").asString()).isEqualTo("JOIN_REQUIRED");
        assertThat(client.isOpen()).isTrue();
    }

    @Test
    @DisplayName("없는 토큰으로 JOIN하면 PARTICIPANT_NOT_FOUND를 받고 연결이 닫힌다")
    void unknownToken() throws Exception {
        TestClient client = connect(null);

        client.send(join("00000000-0000-0000-0000-000000000000", roomId));

        JsonNode error = client.next();
        assertThat(error.path("type").asString()).isEqualTo("ERROR");
        assertThat(error.path("payload").path("code").asString()).isEqualTo("PARTICIPANT_NOT_FOUND");
        assertThat(error.path("payload").path("closed").asBoolean()).isTrue();
        client.awaitClosed();
    }

    @Test
    @DisplayName("REST 입장을 하지 않은 참가자는 NOT_ENTERED_ROOM을 받는다")
    void notEnteredRoom() throws Exception {
        ParticipantCreateResponse notEntered = participantService.create(new ParticipantCreateRequest("민서", "CHARACTER_02"));
        TestClient client = connect(null);

        client.send(join(notEntered.clientToken(), roomId));

        assertThat(client.next().path("payload").path("code").asString()).isEqualTo("NOT_ENTERED_ROOM");
        client.awaitClosed();
    }

    @Test
    @DisplayName("없는 운동방으로 JOIN하면 ROOM_NOT_FOUND를 받는다")
    void unknownRoom() throws Exception {
        ParticipantCreateResponse participant = enteredParticipant("주영");
        TestClient client = connect(null);

        client.send(join(participant.clientToken(), 999_999L));

        assertThat(client.next().path("payload").path("code").asString()).isEqualTo("ROOM_NOT_FOUND");
        client.awaitClosed();
    }

    @Test
    @DisplayName("정원 5명이 찬 방에 여섯 번째로 들어오면 ROOM_FULL을 받는다")
    void roomFull() throws Exception {
        for (int i = 1; i <= 5; i++) {
            joinedClient(enteredParticipant("참가자" + i));
        }
        TestClient sixth = connect(null);

        sixth.send(join(enteredParticipant("여섯째").clientToken(), roomId));

        assertThat(sixth.next().path("payload").path("code").asString()).isEqualTo("ROOM_FULL");
        sixth.awaitClosed();
        assertThat(registry.participants(roomId)).hasSize(5);
    }

    @Test
    @DisplayName("연결이 끊기면 친구 화면에 연결 불안정으로 보이고, 같은 토큰으로 다시 들어오면 연결됨으로 돌아온다")
    void disconnectAndReconnect() throws Exception {
        ParticipantCreateResponse juyoung = enteredParticipant("주영");
        ParticipantCreateResponse jisu = enteredParticipant("지수");
        TestClient first = joinedClient(juyoung);
        TestClient second = joinedClient(jisu);
        first.next(); // 지수 입장 알림

        first.abort();

        JsonNode unstable = second.next();
        assertThat(unstable.path("type").asString()).isEqualTo("CONNECTION_STATUS");
        assertThat(unstable.path("participantId").asLong()).isEqualTo(juyoung.participantId());
        assertThat(unstable.path("payload").path("connectionStatus").asString()).isEqualTo("UNSTABLE");
        assertThat(registry.participants(roomId)).hasSize(2);

        TestClient again = joinedClient(juyoung);

        JsonNode connected = second.next();
        assertThat(connected.path("payload").path("connectionStatus").asString()).isEqualTo("CONNECTED");
        assertThat(registry.participants(roomId))
                .allSatisfy(view -> assertThat(view.connectionStatus()).isEqualTo(ConnectionStatus.CONNECTED));
        assertThat(again.isOpen()).isTrue();
    }

    @Test
    @DisplayName("LEAVE를 보내면 바로 퇴장하고 친구 화면에 퇴장 알림이 간다")
    void leave() throws Exception {
        ParticipantCreateResponse juyoung = enteredParticipant("주영");
        ParticipantCreateResponse jisu = enteredParticipant("지수");
        TestClient first = joinedClient(juyoung);
        TestClient second = joinedClient(jisu);
        first.next(); // 지수 입장 알림

        first.send("{\"type\":\"LEAVE\"}");

        JsonNode left = second.next();
        assertThat(left.path("type").asString()).isEqualTo("PARTICIPANT_LEFT");
        assertThat(left.path("participantId").asLong()).isEqualTo(juyoung.participantId());
        assertThat(left.path("payload").path("reason").asString()).isEqualTo("LEFT");
        first.awaitClosed();
        assertThat(registry.participants(roomId)).hasSize(1);
    }

    @Test
    @DisplayName("허용하지 않은 출처(Origin)에서 오는 연결은 거절된다")
    void disallowedOrigin() {
        assertThatThrownBy(() -> connect("http://evil.example"))
                .isInstanceOf(ExecutionException.class);
    }

    @Test
    @DisplayName("허용한 프론트 출처에서 오는 연결은 맺어진다")
    void allowedOrigin() throws Exception {
        TestClient client = connect("http://localhost:5173");

        assertThat(client.isOpen()).isTrue();
    }

    // ===== 동작 정보(POSE) — 이슈 #4 =====

    /** 관절 n개짜리 POSE 메시지 */
    private static String pose(int landmarkCount) {
        StringBuilder builder = new StringBuilder("{\"type\":\"POSE\",\"payload\":{\"landmarks\":[");
        for (int i = 0; i < landmarkCount; i++) {
            builder.append(i == 0 ? "" : ",").append("[0.012,-0.531,0.104,0.98]");
        }
        return builder.append("]}}").toString();
    }

    @Test
    @DisplayName("POSE를 보내면 다른 참가자가 PARTICIPANT_POSE로 같은 관절 배열을 받는다")
    void poseRelayed() throws Exception {
        ParticipantCreateResponse juyoung = enteredParticipant("주영");
        ParticipantCreateResponse jisu = enteredParticipant("지수");
        TestClient first = joinedClient(juyoung);
        TestClient second = joinedClient(jisu);
        first.next(); // 지수 입장 알림

        first.send(pose(13));

        JsonNode relayed = second.next();
        assertThat(relayed.path("type").asString()).isEqualTo("PARTICIPANT_POSE");
        assertThat(relayed.path("roomId").asLong()).isEqualTo(roomId);
        assertThat(relayed.path("participantId").asLong()).isEqualTo(juyoung.participantId());
        assertThat(relayed.path("sentAt").asString()).isNotBlank();
        assertThat(relayed.path("payload").propertyNames()).containsExactly("landmarks");
        assertThat(relayed.path("payload").path("landmarks")).hasSize(13);
        assertThat(relayed.path("payload").path("landmarks").get(0).get(1).asDouble()).isEqualTo(-0.531);

        // 보낸 사람에게는 돌아오지 않는다: 다음으로 받는 것은 PING의 응답이어야 한다
        first.send("{\"type\":\"PING\"}");
        assertThat(first.next().path("type").asString()).isEqualTo("PONG");
    }

    @Test
    @DisplayName("2KB를 넘는 POSE와 모양이 틀린 POSE는 INVALID_MESSAGE를 받고 연결은 유지된다")
    void invalidPose() throws Exception {
        TestClient client = joinedClient(enteredParticipant("주영"));
        String oversized = "{\"type\":\"POSE\",\"payload\":{\"landmarks\":[[0.1,0.2,0.3]],\"pad\":\"" + "x".repeat(2100) + "\"}}";

        client.send(oversized);
        JsonNode error = client.next();
        assertThat(error.path("payload").path("code").asString()).isEqualTo("INVALID_MESSAGE");
        assertThat(error.path("payload").path("closed").asBoolean()).isFalse();

        client.send("{\"type\":\"POSE\",\"payload\":{\"landmarks\":[[0.1,0.2]]}}");
        assertThat(client.next().path("payload").path("code").asString()).isEqualTo("INVALID_MESSAGE");

        client.send(pose(33)); // 33개는 약 0.9KB라 2KB 안에 든다
        client.send("{\"type\":\"PING\"}");
        assertThat(client.next().path("type").asString()).as("정상 POSE에는 오류가 오지 않는다").isEqualTo("PONG");
        assertThat(client.isOpen()).isTrue();
    }

    // ===== 응원(CHEER) — 이슈 #6 =====

    private static String cheer(Long targetParticipantId, String reaction) {
        return "{\"type\":\"CHEER\",\"payload\":{\"targetParticipantId\":" + targetParticipantId
                + ",\"reaction\":\"" + reaction + "\"}}";
    }

    /** 세 명을 입장시키고 입장 알림을 모두 읽어 둔다 */
    private List<TestClient> threeJoined(ParticipantCreateResponse a, ParticipantCreateResponse b, ParticipantCreateResponse c)
            throws Exception {
        TestClient first = joinedClient(a);
        TestClient second = joinedClient(b);
        first.next(); // b 입장
        TestClient third = joinedClient(c);
        first.next(); // c 입장
        second.next(); // c 입장
        return List.of(first, second, third);
    }

    @Test
    @DisplayName("A가 B에게 보낸 응원은 B만 받고 C는 받지 않으며, A는 CHEER_SENT로 대상을 확인하고 저장으로 넘어간다")
    void cheerOnlyToTarget() throws Exception {
        ParticipantCreateResponse a = enteredParticipant("주영");
        ParticipantCreateResponse b = enteredParticipant("지수");
        ParticipantCreateResponse c = enteredParticipant("민호");
        List<TestClient> joined = threeJoined(a, b, c);

        joined.get(0).send(cheer(b.participantId(), "FIRE"));

        JsonNode received = joined.get(1).next();
        assertThat(received.path("type").asString()).isEqualTo("CHEER_RECEIVED");
        assertThat(received.path("participantId").asLong()).isEqualTo(a.participantId());
        assertThat(received.path("payload").path("reaction").asString()).isEqualTo("FIRE");

        JsonNode sent = joined.get(0).next();
        assertThat(sent.path("type").asString()).isEqualTo("CHEER_SENT");
        assertThat(sent.path("payload").path("targetParticipantId").asLong()).isEqualTo(b.participantId());

        joined.get(2).send("{\"type\":\"PING\"}");
        assertThat(joined.get(2).next().path("type").asString())
                .as("C가 다음으로 받는 것은 응원이 아니라 PING 응답이어야 한다").isEqualTo("PONG");

        await().atMost(WAIT_SECONDS, TimeUnit.SECONDS).until(() -> !RecordingCheerRecorder.RECORDED.isEmpty());
        CheerRecordCommand saved = RecordingCheerRecorder.RECORDED.getFirst();
        assertThat(saved.roomId()).isEqualTo(roomId);
        assertThat(saved.senderId()).isEqualTo(a.participantId());
        assertThat(saved.receiverId()).isEqualTo(b.participantId());
        assertThat(saved.reaction()).isEqualTo("FIRE");
        assertThat(saved.occurredAt().toString()).isEqualTo(received.path("sentAt").asString());
    }

    @Test
    @DisplayName("퇴장한 친구에게 응원하면 CHEER_TARGET_UNAVAILABLE을 받고 연결은 유지되며 저장하지 않는다")
    void cheerToLeftTarget() throws Exception {
        ParticipantCreateResponse a = enteredParticipant("주영");
        ParticipantCreateResponse b = enteredParticipant("지수");
        TestClient first = joinedClient(a);
        TestClient second = joinedClient(b);
        first.next(); // b 입장
        second.send("{\"type\":\"LEAVE\"}");
        assertThat(first.next().path("type").asString()).isEqualTo("PARTICIPANT_LEFT");

        first.send(cheer(b.participantId(), "FIRE"));

        JsonNode error = first.next();
        assertThat(error.path("payload").path("code").asString()).isEqualTo("CHEER_TARGET_UNAVAILABLE");
        assertThat(error.path("payload").path("closed").asBoolean()).isFalse();
        assertThat(first.isOpen()).isTrue();
        assertThat(RecordingCheerRecorder.RECORDED).isEmpty();
    }

    @Test
    @DisplayName("리액션 코드 형식이 틀리면 INVALID_MESSAGE를 받고 연결은 유지된다")
    void invalidCheer() throws Exception {
        ParticipantCreateResponse a = enteredParticipant("주영");
        TestClient client = joinedClient(a);

        client.send(cheer(99L, "fire"));
        assertThat(client.next().path("payload").path("code").asString()).isEqualTo("INVALID_MESSAGE");
        client.send("{\"type\":\"CHEER\",\"payload\":{\"reaction\":\"FIRE\"}}");
        assertThat(client.next().path("payload").path("code").asString()).isEqualTo("INVALID_MESSAGE");
        assertThat(client.isOpen()).isTrue();
    }

    @Test
    @DisplayName("저장이 실패해도 응원은 전달되고 보낸 사람에게 오류가 가지 않는다")
    void cheerDeliveredEvenIfRecordFails() throws Exception {
        ParticipantCreateResponse a = enteredParticipant("주영");
        ParticipantCreateResponse b = enteredParticipant("지수");
        TestClient first = joinedClient(a);
        TestClient second = joinedClient(b);
        first.next(); // b 입장

        first.send(cheer(b.participantId(), RecordingCheerRecorder.FAILING_REACTION));

        assertThat(second.next().path("type").asString()).isEqualTo("CHEER_RECEIVED");
        assertThat(first.next().path("type").asString()).isEqualTo("CHEER_SENT");
        first.send("{\"type\":\"PING\"}");
        assertThat(first.next().path("type").asString()).as("오류 없이 다음 응답이 와야 한다").isEqualTo("PONG");
    }

    /**
     * 테스트용 응원 저장 구현체. 데이터 담당의 실제 구현 대신 넘어온 응원을 모아 둔다.
     * 리액션이 {@link #FAILING_REACTION}이면 저장 실패를 흉내 내 예외를 던진다.
     */
    @TestConfiguration
    static class RecordingCheerRecorder {

        static final List<CheerRecordCommand> RECORDED = new CopyOnWriteArrayList<>();
        static final String FAILING_REACTION = "SAVE_FAIL";

        /** 실제 구현체({@code CheerService})보다 이 빈을 먼저 쓰도록 {@code @Primary}로 둔다 */
        @Bean
        @Primary
        CheerRecorder recordingCheerRecorder() {
            return command -> {
                if (FAILING_REACTION.equals(command.reaction())) {
                    throw new IllegalStateException("저장 실패 테스트");
                }
                RECORDED.add(command);
            };
        }
    }

    /**
     * 받은 메시지를 큐에 모으는 테스트용 WebSocket 클라이언트.
     */
    private static final class TestClient extends TextWebSocketHandler {

        private final BlockingQueue<String> received = new LinkedBlockingQueue<>();
        private volatile boolean closed;
        private WebSocketSession session;

        static TestClient connect(URI uri, String origin) throws Exception {
            TestClient client = new TestClient();
            WebSocketHttpHeaders headers = new WebSocketHttpHeaders();
            if (origin != null) {
                headers.setOrigin(origin);
            }
            CompletableFuture<WebSocketSession> future = new StandardWebSocketClient().execute(client, headers, uri);
            client.session = future.get(WAIT_SECONDS, TimeUnit.SECONDS);
            return client;
        }

        @Override
        protected void handleTextMessage(WebSocketSession session, TextMessage message) {
            received.add(message.getPayload());
        }

        @Override
        public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
            closed = true;
        }

        void send(String json) throws Exception {
            session.sendMessage(new TextMessage(json));
        }

        String nextRaw() throws InterruptedException {
            String message = received.poll(WAIT_SECONDS, TimeUnit.SECONDS);
            assertThat(message).as("서버 메시지를 기다렸지만 오지 않았다").isNotNull();
            return message;
        }

        JsonNode next() throws InterruptedException {
            return MAPPER.readTree(nextRaw());
        }

        boolean isOpen() {
            return session.isOpen() && !closed;
        }

        void awaitClosed() {
            await().atMost(WAIT_SECONDS, TimeUnit.SECONDS).until(() -> closed);
        }

        /** LEAVE 없이 연결만 끊는다(브라우저 종료·네트워크 끊김 흉내) */
        void abort() throws Exception {
            session.close(CloseStatus.GOING_AWAY);
        }

        void closeQuietly() {
            try {
                if (session != null && session.isOpen()) {
                    session.close();
                }
            } catch (Exception ignored) {
                // 테스트 정리 단계라 무시한다
            }
        }
    }
}
