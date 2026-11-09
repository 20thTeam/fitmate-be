package com.team20.fitmate.realtime.room;

import com.team20.fitmate.realtime.message.ParticipantView;
import com.team20.fitmate.realtime.message.RealtimeErrorCode;
import com.team20.fitmate.realtime.message.StatusPayload;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link RoomSessionRegistry}의 방 상태 규칙을 가짜 연결과 바꿀 수 있는 시계로 확인한다.
 * 실제 네트워크 연결 흐름은 {@code RoomWebSocketHandlerIntegrationTest}에서 본다.
 */
class RoomSessionRegistryTest {

    private static final Long ROOM_ID = 1L;
    private static final int CAPACITY = 5;

    /** 테스트용 시간 설정: JOIN 10초, 응답 30초, 재접속 30초 */
    private final RealtimeProperties properties = new RealtimeProperties(
            Duration.ofSeconds(10), Duration.ofSeconds(30), Duration.ofSeconds(30), Duration.ofSeconds(5), 65536);

    private MutableClock clock;
    private RoomSessionRegistry registry;

    @BeforeEach
    void setUp() {
        clock = new MutableClock(Instant.parse("2026-11-09T12:00:00Z"));
        registry = new RoomSessionRegistry(properties, JsonMapper.builder().build(), clock);
    }

    /** 연결을 만들고 참가자로 입장시킨다 */
    private FakeWebSocketSession joined(String sessionId, long participantId, String nickname) {
        FakeWebSocketSession session = new FakeWebSocketSession(sessionId);
        registry.connect(session);
        registry.join(sessionId, ticket(participantId, nickname));
        return session;
    }

    private JoinTicket ticket(long participantId, String nickname) {
        return new JoinTicket(ROOM_ID, CAPACITY, participantId, nickname, "CHARACTER_01");
    }

    private ParticipantView viewOf(long participantId) {
        return registry.participants(ROOM_ID).stream()
                .filter(view -> view.participantId() == participantId)
                .findFirst().orElseThrow();
    }

    @Test
    @DisplayName("입장하면 본인은 스냅샷을, 먼저 들어온 참가자는 입장 알림을 받는다")
    void joinSendsSnapshotAndJoined() {
        FakeWebSocketSession first = joined("s1", 1, "주영");
        FakeWebSocketSession second = joined("s2", 2, "지수");

        JsonNode snapshot = second.last();
        assertThat(snapshot.path("type").asString()).isEqualTo("ROOM_SNAPSHOT");
        assertThat(snapshot.path("participantId").asLong()).isEqualTo(2);
        assertThat(snapshot.path("payload").path("capacity").asInt()).isEqualTo(CAPACITY);
        assertThat(snapshot.path("payload").path("participants")).hasSize(2);

        JsonNode joinedEvent = first.last();
        assertThat(joinedEvent.path("type").asString()).isEqualTo("PARTICIPANT_JOINED");
        assertThat(joinedEvent.path("payload").path("nickname").asString()).isEqualTo("지수");
        assertThat(joinedEvent.path("payload").path("progressStatus").asString()).isEqualTo("WAITING");
        assertThat(joinedEvent.path("payload").path("connectionStatus").asString()).isEqualTo("CONNECTED");
    }

    @Test
    @DisplayName("같은 닉네임이어도 참가자 식별값이 다르면 서로 다른 참가자다")
    void sameNicknameIsDifferentParticipant() {
        joined("s1", 1, "주영");
        joined("s2", 2, "주영");

        assertThat(registry.participants(ROOM_ID)).extracting(ParticipantView::participantId).containsExactly(1L, 2L);
    }

    @Test
    @DisplayName("정원이 차면 다음 참가자는 ROOM_FULL을 받고 연결이 닫힌다")
    void roomFull() {
        for (int i = 1; i <= CAPACITY; i++) {
            joined("s" + i, i, "참가자" + i);
        }

        FakeWebSocketSession sixth = joined("s6", 6, "여섯째");

        assertThat(sixth.last().path("payload").path("code").asString()).isEqualTo("ROOM_FULL");
        assertThat(sixth.last().path("payload").path("closed").asBoolean()).isTrue();
        assertThat(sixth.closeStatus()).isNotNull();
        assertThat(registry.participants(ROOM_ID)).hasSize(CAPACITY);
    }

    @Test
    @DisplayName("상태 변경은 다른 참가자에게만 전달되고 받은 쪽의 진행 상태는 바뀌지 않는다")
    void statusIsRelayedWithoutChangingOthers() {
        FakeWebSocketSession first = joined("s1", 1, "주영");
        FakeWebSocketSession second = joined("s2", 2, "지수");
        int secondBefore = second.sent().size();

        registry.updateStatus("s1", new StatusPayload(3L, "스쿼트", ProgressStatus.EXERCISING));

        JsonNode relayed = second.last();
        assertThat(relayed.path("type").asString()).isEqualTo("PARTICIPANT_STATUS");
        assertThat(relayed.path("participantId").asLong()).isEqualTo(1);
        assertThat(relayed.path("payload").path("exerciseName").asString()).isEqualTo("스쿼트");
        assertThat(second.sent()).hasSize(secondBefore + 1);
        assertThat(first.sentOf("PARTICIPANT_STATUS")).isEmpty();
        assertThat(viewOf(2).progressStatus()).isEqualTo(ProgressStatus.WAITING);
        assertThat(viewOf(1).progressStatus()).isEqualTo(ProgressStatus.EXERCISING);
    }

    @Test
    @DisplayName("JOIN 전에 STATUS를 보내면 JOIN_REQUIRED를 받고 연결은 유지된다")
    void statusBeforeJoin() {
        FakeWebSocketSession session = new FakeWebSocketSession("s1");
        registry.connect(session);

        registry.updateStatus("s1", new StatusPayload(null, null, ProgressStatus.WAITING));

        assertThat(session.last().path("payload").path("code").asString()).isEqualTo("JOIN_REQUIRED");
        assertThat(session.isOpen()).isTrue();
    }

    @Test
    @DisplayName("연결이 끊기면 퇴장이 아니라 연결 불안정으로 표시하고 다른 참가자에게 알린다")
    void disconnectMarksUnstable() {
        joined("s1", 1, "주영");
        FakeWebSocketSession second = joined("s2", 2, "지수");

        registry.disconnected("s1");

        assertThat(viewOf(1).connectionStatus()).isEqualTo(ConnectionStatus.UNSTABLE);
        JsonNode event = second.last();
        assertThat(event.path("type").asString()).isEqualTo("CONNECTION_STATUS");
        assertThat(event.path("participantId").asLong()).isEqualTo(1);
        assertThat(event.path("payload").path("connectionStatus").asString()).isEqualTo("UNSTABLE");
    }

    @Test
    @DisplayName("재접속 대기 시간 안에 다시 들어오면 진행 상태를 유지한 채 연결됨으로 돌아온다")
    void reconnectWithinGraceKeepsStatus() {
        joined("s1", 1, "주영");
        FakeWebSocketSession second = joined("s2", 2, "지수");
        registry.updateStatus("s1", new StatusPayload(3L, "스쿼트", ProgressStatus.EXERCISING));
        registry.disconnected("s1");
        clock.advance(Duration.ofSeconds(20));

        FakeWebSocketSession again = joined("s1-again", 1, "주영");

        assertThat(again.last().path("type").asString()).isEqualTo("ROOM_SNAPSHOT");
        ParticipantView me = viewOf(1);
        assertThat(me.connectionStatus()).isEqualTo(ConnectionStatus.CONNECTED);
        assertThat(me.progressStatus()).isEqualTo(ProgressStatus.EXERCISING);
        assertThat(second.last().path("payload").path("connectionStatus").asString()).isEqualTo("CONNECTED");
        assertThat(registry.participants(ROOM_ID)).hasSize(2);
    }

    @Test
    @DisplayName("재접속 대기 시간이 지나면 퇴장 처리하고 TIMEOUT 이유로 알린다")
    void reconnectGraceExpired() {
        joined("s1", 1, "주영");
        FakeWebSocketSession second = joined("s2", 2, "지수");
        registry.disconnected("s1");
        registry.ping("s2");

        clock.advance(Duration.ofSeconds(29));
        registry.sweep();
        assertThat(registry.participants(ROOM_ID)).hasSize(2);

        clock.advance(Duration.ofSeconds(1));
        registry.ping("s2");
        registry.sweep();

        assertThat(registry.participants(ROOM_ID)).extracting(ParticipantView::participantId).containsExactly(2L);
        JsonNode left = second.sentOf("PARTICIPANT_LEFT").getLast();
        assertThat(left.path("participantId").asLong()).isEqualTo(1);
        assertThat(left.path("payload").path("reason").asString()).isEqualTo("TIMEOUT");
    }

    @Test
    @DisplayName("응답 대기 시간 동안 아무 메시지가 없으면 연결 불안정으로 바꾸고 연결을 닫는다")
    void heartbeatTimeout() {
        FakeWebSocketSession first = joined("s1", 1, "주영");
        FakeWebSocketSession second = joined("s2", 2, "지수");

        clock.advance(Duration.ofSeconds(29));
        registry.ping("s2");
        registry.sweep();
        assertThat(viewOf(1).connectionStatus()).isEqualTo(ConnectionStatus.CONNECTED);

        clock.advance(Duration.ofSeconds(1));
        registry.sweep();

        assertThat(viewOf(1).connectionStatus()).isEqualTo(ConnectionStatus.UNSTABLE);
        assertThat(first.closeStatus().getCode()).isEqualTo(4500);
        assertThat(first.closeStatus().getReason())
                .as("Spring 기본 사유(전송 시간 초과)가 아니라 응답 없음 사유로 닫아야 한다")
                .isEqualTo("HEARTBEAT_TIMEOUT");
        assertThat(viewOf(2).connectionStatus()).isEqualTo(ConnectionStatus.CONNECTED);
        assertThat(second.last().path("payload").path("connectionStatus").asString()).isEqualTo("UNSTABLE");
    }

    @Test
    @DisplayName("JOIN 없이 대기 시간이 지나면 JOIN_TIMEOUT을 받고 연결이 닫힌다")
    void joinTimeout() {
        FakeWebSocketSession session = new FakeWebSocketSession("s1");
        registry.connect(session);

        clock.advance(Duration.ofSeconds(10));
        registry.sweep();

        assertThat(session.last().path("payload").path("code").asString()).isEqualTo("JOIN_TIMEOUT");
        assertThat(session.closeStatus()).isNotNull();
    }

    @Test
    @DisplayName("직접 퇴장하면 바로 빠지고 다른 참가자에게 LEFT 이유로 알린다")
    void leave() {
        FakeWebSocketSession first = joined("s1", 1, "주영");
        FakeWebSocketSession second = joined("s2", 2, "지수");

        registry.leave("s1");

        assertThat(registry.participants(ROOM_ID)).extracting(ParticipantView::participantId).containsExactly(2L);
        assertThat(first.closeStatus()).isNotNull();
        JsonNode left = second.last();
        assertThat(left.path("type").asString()).isEqualTo("PARTICIPANT_LEFT");
        assertThat(left.path("payload").path("reason").asString()).isEqualTo("LEFT");
        assertThat(viewOf(2).progressStatus()).isEqualTo(ProgressStatus.WAITING);
    }

    @Test
    @DisplayName("같은 참가자가 다른 창으로 입장하면 예전 연결은 SESSION_REPLACED로 닫히고 자리는 하나만 쓴다")
    void sameParticipantNewTab() {
        FakeWebSocketSession oldTab = joined("s1", 1, "주영");
        FakeWebSocketSession second = joined("s2", 2, "지수");
        int secondBefore = second.sent().size();

        FakeWebSocketSession newTab = joined("s1-new", 1, "주영");

        assertThat(oldTab.last().path("payload").path("code").asString()).isEqualTo("SESSION_REPLACED");
        assertThat(oldTab.closeStatus()).isNotNull();
        assertThat(newTab.last().path("type").asString()).isEqualTo("ROOM_SNAPSHOT");
        assertThat(registry.participants(ROOM_ID)).hasSize(2);
        assertThat(second.sent()).hasSize(secondBefore);

        registry.disconnected("s1");
        assertThat(viewOf(1).connectionStatus()).isEqualTo(ConnectionStatus.CONNECTED);
    }

    @Test
    @DisplayName("이미 입장한 연결에서 JOIN을 다시 보내면 ALREADY_JOINED를 받고 연결은 유지된다")
    void alreadyJoined() {
        FakeWebSocketSession session = joined("s1", 1, "주영");

        registry.join("s1", ticket(1, "주영"));

        assertThat(session.last().path("payload").path("code").asString())
                .isEqualTo(RealtimeErrorCode.ALREADY_JOINED.name());
        assertThat(session.isOpen()).isTrue();
    }

    @Test
    @DisplayName("모든 참가자가 나가면 방 상태도 지워진다")
    void emptyRoomRemoved() {
        joined("s1", 1, "주영");

        registry.leave("s1");

        assertThat(registry.participants(ROOM_ID)).isEmpty();
    }

    /**
     * 테스트에서 시간을 앞으로 돌릴 수 있는 시계.
     */
    static final class MutableClock extends Clock {

        private Instant now;

        MutableClock(Instant start) {
            this.now = start;
        }

        void advance(Duration duration) {
            now = now.plus(duration);
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
}
