package com.team20.fitmate.realtime.handler;

import com.team20.fitmate.domain.contest.ContestTestClock;
import com.team20.fitmate.domain.contest.dto.ContestJoinRequest;
import com.team20.fitmate.domain.contest.dto.ContestJoinResponse;
import com.team20.fitmate.domain.contest.repository.ContestGameRepository;
import com.team20.fitmate.domain.contest.service.ContestService;
import com.team20.fitmate.realtime.room.RoomSessionRegistry;
import com.team20.fitmate.realtime.room.SpaceKey;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import org.springframework.boot.test.web.server.LocalServerPort;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * 챌린지 판 실시간 입장(이슈 #34 추가 요청)을 실제 서버와 실제 WebSocket 클라이언트로 확인한다.
 * <p>
 * 판 참가는 REST와 같은 {@link ContestService#join}으로 만들고, 시각은 {@link ContestTestClock}으로 고정한다.
 * 실시간 응답 대기 시간(30초)을 넘기지 않도록 판은 시간 경과가 아니라 모두 제출로 끝낸다.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(ContestTestClock.class)
class ContestRealtimeIntegrationTest {

    private static final JsonMapper MAPPER = JsonMapper.builder().build();
    private static final long WAIT_SECONDS = 5;

    @LocalServerPort
    private int port;

    @Autowired
    private ContestService contestService;

    @Autowired
    private ContestGameRepository gameRepository;

    @Autowired
    private RoomSessionRegistry registry;

    @Autowired
    private ContestTestClock.MutableClock clock;

    private final List<TestClient> clients = new ArrayList<>();

    @BeforeEach
    void setUp() {
        gameRepository.deleteAll();
        clock.set(ContestTestClock.BASE);
    }

    @AfterEach
    void tearDown() {
        clients.forEach(TestClient::closeQuietly);
        registry.clear();
        gameRepository.deleteAll();
    }

    @Test
    @DisplayName("contestGameId·playerId로 JOIN하면 판 정원 4명짜리 스냅샷을 받고, 입장·상태·동작 정보·퇴장이 같은 판에 전달된다")
    void joinAndRelay() throws Exception {
        ContestJoinResponse minjun = join("민준", "seoultech");
        ContestJoinResponse jiho = join("지호", "khu");
        String gameId = minjun.game().gameId();

        TestClient a = connect();
        a.send(joinContest(gameId, minjun.playerId()));
        JsonNode snapshot = a.nextOfType("ROOM_SNAPSHOT");
        assertThat(snapshot.path("contestGameId").asLong()).isEqualTo(Long.parseLong(gameId));
        assertThat(snapshot.path("roomId").isNull()).isTrue();
        assertThat(snapshot.path("partyId").isNull()).isTrue();
        assertThat(snapshot.path("participantId").asLong()).isEqualTo(Long.parseLong(minjun.playerId()));
        assertThat(snapshot.path("payload").path("capacity").asInt()).isEqualTo(4);
        JsonNode me = snapshot.path("payload").path("participants").get(0);
        assertThat(me.path("participantId").asLong()).isEqualTo(Long.parseLong(minjun.playerId()));
        assertThat(me.path("nickname").asString()).isEqualTo("민준");
        assertThat(me.path("characterCode").isNull()).isTrue();

        TestClient b = connect();
        b.send(joinContest(gameId, jiho.playerId()));
        assertThat(b.nextOfType("ROOM_SNAPSHOT").path("payload").path("participants").size()).isEqualTo(2);
        JsonNode joined = a.nextOfType("PARTICIPANT_JOINED");
        assertThat(joined.path("participantId").asLong()).isEqualTo(Long.parseLong(jiho.playerId()));
        assertThat(joined.path("payload").path("nickname").asString()).isEqualTo("지호");

        b.send("{\"type\":\"STATUS\",\"payload\":{\"exerciseName\":\"스쿼트\",\"progressStatus\":\"EXERCISING\"}}");
        JsonNode status = a.nextOfType("PARTICIPANT_STATUS");
        assertThat(status.path("participantId").asLong()).isEqualTo(Long.parseLong(jiho.playerId()));
        assertThat(status.path("contestGameId").asLong()).isEqualTo(Long.parseLong(gameId));

        a.send("{\"type\":\"POSE\",\"payload\":{\"landmarks\":[[0.1,0.2,0.3],[0.4,0.5,0.6,0.9]]}}");
        JsonNode pose = b.nextOfType("PARTICIPANT_POSE");
        assertThat(pose.path("participantId").asLong()).isEqualTo(Long.parseLong(minjun.playerId()));
        assertThat(pose.path("payload").path("landmarks").size()).isEqualTo(2);

        b.send("{\"type\":\"LEAVE\"}");
        JsonNode left = a.nextOfType("PARTICIPANT_LEFT");
        assertThat(left.path("participantId").asLong()).isEqualTo(Long.parseLong(jiho.playerId()));
        assertThat(left.path("payload").path("reason").asString()).isEqualTo("LEFT");
    }

    @Test
    @DisplayName("숫자로 보낸 contestGameId·playerId도 받는다")
    void joinWithNumbers() throws Exception {
        ContestJoinResponse minjun = join("민준", "seoultech");
        TestClient client = connect();
        client.send("{\"type\":\"JOIN\",\"payload\":{\"contestGameId\":" + minjun.game().gameId()
                + ",\"playerId\":" + minjun.playerId() + "}}");
        assertThat(client.next().path("type").asString()).isEqualTo("ROOM_SNAPSHOT");
    }

    @Test
    @DisplayName("판의 참가자가 아니면 CONTEST_PLAYER_NOT_FOUND, 없는 판은 CONTEST_GAME_NOT_FOUND로 닫히고, 형식 오류는 INVALID_MESSAGE")
    void joinErrors() throws Exception {
        ContestJoinResponse minjun = join("민준", "seoultech");
        String gameId = minjun.game().gameId();

        TestClient stranger = connect();
        stranger.send(joinContest(gameId, "999999"));
        JsonNode notPlayer = stranger.next();
        assertThat(notPlayer.path("type").asString()).isEqualTo("ERROR");
        assertThat(notPlayer.path("payload").path("code").asString()).isEqualTo("CONTEST_PLAYER_NOT_FOUND");
        assertThat(notPlayer.path("payload").path("closed").asBoolean()).isTrue();
        stranger.awaitClosed();

        TestClient noGame = connect();
        noGame.send(joinContest("999999", minjun.playerId()));
        assertThat(noGame.next().path("payload").path("code").asString()).isEqualTo("CONTEST_GAME_NOT_FOUND");
        noGame.awaitClosed();

        // 다른 판의 참가자로는 들어갈 수 없다
        contestService.start(Long.valueOf(gameId), Long.valueOf(minjun.playerId()));
        ContestJoinResponse other = join("지호", "khu");
        TestClient wrongGame = connect();
        wrongGame.send(joinContest(gameId, other.playerId()));
        assertThat(wrongGame.next().path("payload").path("code").asString()).isEqualTo("CONTEST_PLAYER_NOT_FOUND");

        TestClient missingPlayer = connect();
        missingPlayer.send("{\"type\":\"JOIN\",\"payload\":{\"contestGameId\":\"" + gameId + "\"}}");
        assertThat(missingPlayer.next().path("payload").path("code").asString()).isEqualTo("INVALID_MESSAGE");
        assertThat(missingPlayer.isOpen()).isTrue();

        TestClient both = connect();
        both.send("{\"type\":\"JOIN\",\"payload\":{\"contestGameId\":\"" + gameId + "\",\"playerId\":\""
                + minjun.playerId() + "\",\"roomId\":1}}");
        assertThat(both.next().path("payload").path("code").asString()).isEqualTo("INVALID_MESSAGE");

        assertThat(registry.participants(SpaceKey.contestGame(Long.valueOf(gameId)))).isEmpty();
    }

    @Test
    @DisplayName("판이 FINISHED가 되면 서버가 연결을 1000(CONTEST_GAME_FINISHED)으로 닫고, 끝난 판에는 다시 들어올 수 없다")
    void finishedGameClosesConnections() throws Exception {
        ContestJoinResponse minjun = join("민준", "seoultech");
        ContestJoinResponse jiho = join("지호", "khu");
        Long gameId = Long.valueOf(minjun.game().gameId());

        TestClient a = connect();
        a.send(joinContest(minjun.game().gameId(), minjun.playerId()));
        a.nextOfType("ROOM_SNAPSHOT");
        TestClient b = connect();
        b.send(joinContest(minjun.game().gameId(), jiho.playerId()));
        b.nextOfType("ROOM_SNAPSHOT");

        contestService.start(gameId, Long.valueOf(minjun.playerId()));
        clock.advance(Duration.ofSeconds(6));
        contestService.submitResult(gameId, Long.valueOf(minjun.playerId()), 12);
        // 한 명이 남아 있으면 아직 끝나지 않았으므로 연결이 유지된다
        assertThat(a.isOpen()).isTrue();
        contestService.submitResult(gameId, Long.valueOf(jiho.playerId()), null);

        a.awaitClosed();
        b.awaitClosed();
        assertThat(a.closeStatus.getCode()).isEqualTo(CloseStatus.NORMAL.getCode());
        assertThat(a.closeStatus.getReason()).isEqualTo("CONTEST_GAME_FINISHED");
        assertThat(registry.participants(SpaceKey.contestGame(gameId))).isEmpty();
        assertThat(registry.activeContestGameIds()).doesNotContain(gameId);

        TestClient late = connect();
        late.send(joinContest(minjun.game().gameId(), minjun.playerId()));
        JsonNode finished = late.next();
        assertThat(finished.path("payload").path("code").asString()).isEqualTo("CONTEST_GAME_FINISHED");
        assertThat(finished.path("payload").path("closed").asBoolean()).isTrue();
        late.awaitClosed();
    }

    private ContestJoinResponse join(String nickname, String schoolId) {
        return contestService.join(new ContestJoinRequest(nickname, schoolId));
    }

    private TestClient connect() throws Exception {
        TestClient client = TestClient.connect(URI.create("ws://localhost:" + port + "/ws"));
        clients.add(client);
        return client;
    }

    /** FE가 보내는 형태 그대로(식별값을 문자열로) 챌린지 판 JOIN을 만든다 */
    private static String joinContest(String gameId, String playerId) {
        return "{\"type\":\"JOIN\",\"payload\":{\"contestGameId\":\"" + gameId + "\",\"playerId\":\"" + playerId + "\"}}";
    }

    /**
     * 받은 메시지와 닫힘 상태를 모으는 테스트용 WebSocket 클라이언트.
     */
    private static final class TestClient extends TextWebSocketHandler {

        private final BlockingQueue<String> received = new LinkedBlockingQueue<>();
        private volatile CloseStatus closeStatus;
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
            closeStatus = status;
        }

        void send(String json) throws Exception {
            session.sendMessage(new TextMessage(json));
        }

        JsonNode next() throws InterruptedException {
            String message = received.poll(WAIT_SECONDS, TimeUnit.SECONDS);
            assertThat(message).as("서버 메시지를 기다렸지만 오지 않았다").isNotNull();
            return MAPPER.readTree(message);
        }

        /** 해당 종류가 올 때까지 다른 메시지는 건너뛴다 */
        JsonNode nextOfType(String type) throws InterruptedException {
            while (true) {
                JsonNode node = next();
                if (type.equals(node.path("type").asString())) {
                    return node;
                }
            }
        }

        boolean isOpen() {
            return session.isOpen() && closeStatus == null;
        }

        void awaitClosed() {
            await().atMost(WAIT_SECONDS, TimeUnit.SECONDS).until(() -> closeStatus != null);
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
