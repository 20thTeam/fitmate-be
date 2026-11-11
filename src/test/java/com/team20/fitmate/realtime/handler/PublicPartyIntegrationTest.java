package com.team20.fitmate.realtime.handler;

import com.team20.fitmate.domain.participant.dto.ParticipantCreateRequest;
import com.team20.fitmate.domain.participant.dto.ParticipantCreateResponse;
import com.team20.fitmate.domain.participant.service.ParticipantService;
import com.team20.fitmate.realtime.room.RoomSessionRegistry;
import com.team20.fitmate.realtime.room.SpaceKey;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * 공개 파티(이슈 #11)를 실제 서버와 실제 WebSocket 클라이언트로 확인한다.
 * 시계를 바꿔 끼워 오픈 시각 전후를 만든다(설정 기본값: 서울 22:00 ~ 23:00).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(PublicPartyIntegrationTest.ClockConfig.class)
class PublicPartyIntegrationTest {

    private static final JsonMapper MAPPER = JsonMapper.builder().build();
    private static final long WAIT_SECONDS = 5;

    /** 서울 22:30 (열림) */
    private static final Instant OPEN = Instant.parse("2026-11-11T13:30:00Z");

    /** 서울 21:00 (닫힘) */
    private static final Instant CLOSED = Instant.parse("2026-11-11T12:00:00Z");

    /** 설정의 공개 파티 식별값 */
    private static final long PARTY_ID = 1L;

    @LocalServerPort
    private int port;

    @Autowired
    private ParticipantService participantService;

    @Autowired
    private RoomSessionRegistry registry;

    @Autowired
    private MutableClock clock;

    private final List<TestClient> clients = new ArrayList<>();

    @BeforeEach
    void setUp() {
        clock.set(OPEN);
    }

    @AfterEach
    void tearDown() {
        clients.forEach(TestClient::closeQuietly);
        registry.clear();
        clock.set(OPEN);
    }

    /** 참가자만 발급한다. 공개 파티는 REST 입장이 필요 없다 */
    private ParticipantCreateResponse participant(String nickname) {
        return participantService.create(new ParticipantCreateRequest(nickname, "CHARACTER_01"));
    }

    private TestClient connect() throws Exception {
        TestClient client = TestClient.connect(URI.create("ws://localhost:" + port + "/ws"));
        clients.add(client);
        return client;
    }

    private static String joinParty(String token, long partyId) {
        return "{\"type\":\"JOIN\",\"payload\":{\"token\":\"" + token + "\",\"partyId\":" + partyId + "}}";
    }

    /** 공개 파티 상태 API를 부른다 */
    private JsonNode getCurrent() throws Exception {
        HttpResponse<String> response = HttpClient.newHttpClient().send(
                HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/public-parties/current")).GET().build(),
                HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).isEqualTo(200);
        return MAPPER.readTree(response.body());
    }

    @Test
    @DisplayName("열려 있는 시간에는 OPEN과 이번 구간 시각을, 닫힌 시간에는 CLOSED와 다음 오픈 시각을 응답한다")
    void currentStatus() throws Exception {
        JsonNode open = getCurrent();
        assertThat(open.path("resultCode").asString()).isEqualTo("200-1");
        assertThat(open.path("data").path("partyId").asLong()).isEqualTo(PARTY_ID);
        assertThat(open.path("data").path("status").asString()).isEqualTo("OPEN");
        assertThat(open.path("data").path("opensAt").asString()).isEqualTo("2026-11-11T22:00:00+09:00");
        assertThat(open.path("data").path("closesAt").asString()).isEqualTo("2026-11-11T23:00:00+09:00");
        assertThat(open.path("data").path("participantCount").asInt()).isZero();

        clock.set(CLOSED);
        JsonNode closed = getCurrent();
        assertThat(closed.path("data").path("status").asString()).isEqualTo("CLOSED");
        assertThat(closed.path("data").path("opensAt").asString()).isEqualTo("2026-11-11T22:00:00+09:00");
    }

    @Test
    @DisplayName("열려 있으면 REST 입장 없이 임시 프로필만으로 partyId JOIN이 되고, 상태 API 인원에 반영된다")
    void joinWhenOpen() throws Exception {
        ParticipantCreateResponse juyoung = participant("주영");
        TestClient client = connect();

        client.send(joinParty(juyoung.clientToken(), PARTY_ID));

        JsonNode snapshot = client.next();
        assertThat(snapshot.path("type").asString()).isEqualTo("ROOM_SNAPSHOT");
        assertThat(snapshot.path("partyId").asLong()).isEqualTo(PARTY_ID);
        assertThat(snapshot.path("roomId").isNull()).isTrue();
        assertThat(snapshot.path("payload").path("capacity").isNull()).isTrue();
        assertThat(snapshot.path("payload").path("participants").get(0).path("participantId").asLong())
                .isEqualTo(juyoung.participantId());
        assertThat(getCurrent().path("data").path("participantCount").asInt()).isEqualTo(1);
    }

    @Test
    @DisplayName("닫혀 있으면 PUBLIC_PARTY_CLOSED를 받고 연결이 닫히며 참가자 자리가 생기지 않는다")
    void joinRejectedWhenClosed() throws Exception {
        clock.set(CLOSED);
        TestClient client = connect();

        client.send(joinParty(participant("주영").clientToken(), PARTY_ID));

        JsonNode error = client.next();
        assertThat(error.path("payload").path("code").asString()).isEqualTo("PUBLIC_PARTY_CLOSED");
        assertThat(error.path("payload").path("closed").asBoolean()).isTrue();
        client.awaitClosed();
        assertThat(registry.participants(SpaceKey.publicParty(PARTY_ID))).isEmpty();
        assertThat(getCurrent().path("data").path("participantCount").asInt()).isZero();
    }

    @Test
    @DisplayName("없는 파티는 PUBLIC_PARTY_NOT_FOUND, 없는 토큰은 PARTICIPANT_NOT_FOUND, roomId와 partyId를 함께 보내면 INVALID_MESSAGE")
    void joinErrors() throws Exception {
        TestClient wrongParty = connect();
        wrongParty.send(joinParty(participant("주영").clientToken(), 999));
        assertThat(wrongParty.next().path("payload").path("code").asString()).isEqualTo("PUBLIC_PARTY_NOT_FOUND");
        wrongParty.awaitClosed();

        TestClient unknownToken = connect();
        unknownToken.send(joinParty("없는-토큰", PARTY_ID));
        assertThat(unknownToken.next().path("payload").path("code").asString()).isEqualTo("PARTICIPANT_NOT_FOUND");

        TestClient both = connect();
        both.send("{\"type\":\"JOIN\",\"payload\":{\"token\":\"" + participant("지수").clientToken()
                + "\",\"roomId\":1,\"partyId\":1}}");
        JsonNode invalid = both.next();
        assertThat(invalid.path("payload").path("code").asString()).isEqualTo("INVALID_MESSAGE");
        assertThat(both.isOpen()).isTrue();
    }

    @Test
    @DisplayName("참가자 20명이 동시에 들어와도 모두 입장하고, 한 명의 상태 변경이 나머지 19명 모두에게 전달된다")
    void manyParticipants() throws Exception {
        int count = 20;
        List<TestClient> members = new ArrayList<>();
        List<ParticipantCreateResponse> profiles = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            profiles.add(participant("참가자" + i));
            members.add(connect());
        }
        for (int i = 0; i < count; i++) {
            members.get(i).send(joinParty(profiles.get(i).clientToken(), PARTY_ID));
        }
        for (TestClient member : members) {
            assertThat(member.nextOfType("ROOM_SNAPSHOT")).isNotNull();
        }
        await().atMost(WAIT_SECONDS, TimeUnit.SECONDS)
                .until(() -> registry.participants(SpaceKey.publicParty(PARTY_ID)).size() == count);

        members.getFirst().send("{\"type\":\"STATUS\",\"payload\":{\"exerciseName\":\"에어로빅\",\"progressStatus\":\"EXERCISING\"}}");

        for (TestClient other : members.subList(1, count)) {
            JsonNode status = other.nextOfType("PARTICIPANT_STATUS");
            assertThat(status.path("participantId").asLong()).isEqualTo(profiles.getFirst().participantId());
            assertThat(status.path("partyId").asLong()).isEqualTo(PARTY_ID);
        }
        assertThat(getCurrent().path("data").path("participantCount").asInt()).isEqualTo(count);
    }

    /**
     * 테스트에서 시각을 바꿀 수 있는 시계를 실제 {@code Clock} 대신 끼운다.
     */
    @TestConfiguration
    static class ClockConfig {

        @Bean
        @Primary
        MutableClock mutableClock() {
            return new MutableClock(OPEN);
        }
    }

    /**
     * 시각을 직접 정하는 시계.
     */
    static final class MutableClock extends Clock {

        private volatile Instant now;

        MutableClock(Instant start) {
            this.now = start;
        }

        void set(Instant instant) {
            this.now = instant;
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }

    /**
     * 받은 메시지를 큐에 모으는 테스트용 WebSocket 클라이언트.
     */
    private static final class TestClient extends TextWebSocketHandler {

        private final BlockingQueue<String> received = new LinkedBlockingQueue<>();
        private volatile boolean closed;
        private WebSocketSession session;

        static TestClient connect(URI uri) throws Exception {
            TestClient client = new TestClient();
            CompletableFuture<WebSocketSession> future =
                    new StandardWebSocketClient().execute(client, new WebSocketHttpHeaders(), uri);
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

        JsonNode next() throws InterruptedException {
            String message = received.poll(WAIT_SECONDS, TimeUnit.SECONDS);
            assertThat(message).as("서버 메시지를 기다렸지만 오지 않았다").isNotNull();
            return MAPPER.readTree(message);
        }

        /** 해당 종류가 올 때까지 다른 메시지는 건너뛴다(입장 알림 등) */
        JsonNode nextOfType(String type) throws InterruptedException {
            while (true) {
                JsonNode node = next();
                if (type.equals(node.path("type").asString())) {
                    return node;
                }
            }
        }

        boolean isOpen() {
            return session.isOpen() && !closed;
        }

        void awaitClosed() {
            await().atMost(WAIT_SECONDS, TimeUnit.SECONDS).until(() -> closed);
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
