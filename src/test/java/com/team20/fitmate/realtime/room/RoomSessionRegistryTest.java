package com.team20.fitmate.realtime.room;

import com.team20.fitmate.domain.cheer.dto.CheerRecordCommand;
import com.team20.fitmate.realtime.message.CheerPayload;
import com.team20.fitmate.realtime.message.ParticipantView;
import com.team20.fitmate.realtime.message.PosePayload;
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
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link RoomSessionRegistry}의 방 상태 규칙을 가짜 연결과 바꿀 수 있는 시계로 확인한다.
 * 실제 네트워크 연결 흐름은 {@code RoomWebSocketHandlerIntegrationTest}에서 본다.
 */
class RoomSessionRegistryTest {

    private static final Long ROOM_ID = 1L;
    private static final int CAPACITY = 5;
    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    /** 테스트용 설정: JOIN 10초, 응답 30초, 재접속 30초, 동작 정보 2KB·초당 15개, 응원 연타 1초 */
    private final RealtimeProperties properties = new RealtimeProperties(
            Duration.ofSeconds(10), Duration.ofSeconds(30), Duration.ofSeconds(30), Duration.ofSeconds(5), 65536,
            2048, 15, Duration.ofSeconds(1));

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

    // ===== 동작 정보(POSE) — 이슈 #4 =====

    /** 관절 두 개짜리 동작 정보 */
    private PosePayload pose() {
        JsonNode payload = MAPPER.readTree("{\"landmarks\":[[0.012,-0.531,0.104,0.98],[0.153,-0.402,0.061,0.97]]}");
        return PosePayload.from(payload);
    }

    @Test
    @DisplayName("동작 정보는 같은 방의 다른 참가자에게만 받은 관절 배열 그대로 전달된다")
    void poseRelayedToOthersOnly() {
        FakeWebSocketSession first = joined("s1", 1, "주영");
        FakeWebSocketSession second = joined("s2", 2, "지수");
        FakeWebSocketSession third = joined("s3", 3, "민호");

        registry.relayPose("s1", pose());

        for (FakeWebSocketSession other : new FakeWebSocketSession[]{second, third}) {
            JsonNode relayed = other.last();
            assertThat(relayed.path("type").asString()).isEqualTo("PARTICIPANT_POSE");
            assertThat(relayed.path("roomId").asLong()).isEqualTo(ROOM_ID);
            assertThat(relayed.path("participantId").asLong()).isEqualTo(1);
            assertThat(relayed.path("payload").path("landmarks").get(0).get(1).asDouble()).isEqualTo(-0.531);
        }
        assertThat(first.sentOf("PARTICIPANT_POSE")).as("보낸 사람에게는 돌아오지 않는다").isEmpty();
    }

    @Test
    @DisplayName("동작 정보를 받아도 참가자 상태(스냅샷)에는 남지 않는다")
    void poseNotKeptInState() {
        joined("s1", 1, "주영");
        joined("s2", 2, "지수");
        registry.relayPose("s1", pose());

        FakeWebSocketSession third = joined("s3", 3, "민호");

        assertThat(third.last().path("type").asString()).isEqualTo("ROOM_SNAPSHOT");
        assertThat(third.last().toString()).doesNotContain("landmarks");
    }

    @Test
    @DisplayName("1초 안에 상한(15개)을 넘는 동작 정보는 버리고, 1초가 지나면 다시 전달한다")
    void poseRateLimited() {
        joined("s1", 1, "주영");
        FakeWebSocketSession second = joined("s2", 2, "지수");

        for (int i = 0; i < 20; i++) {
            registry.relayPose("s1", pose());
            clock.advance(Duration.ofMillis(10));
        }
        assertThat(second.sentOf("PARTICIPANT_POSE")).hasSize(15);

        clock.advance(Duration.ofSeconds(1));
        registry.relayPose("s1", pose());
        assertThat(second.sentOf("PARTICIPANT_POSE")).hasSize(16);
    }

    @Test
    @DisplayName("빈도 상한은 참가자마다 따로 센다")
    void poseRateLimitPerParticipant() {
        joined("s1", 1, "주영");
        joined("s2", 2, "지수");
        FakeWebSocketSession third = joined("s3", 3, "민호");

        for (int i = 0; i < 20; i++) {
            registry.relayPose("s1", pose());
        }
        registry.relayPose("s2", pose());

        assertThat(third.sentOf("PARTICIPANT_POSE"))
                .filteredOn(message -> message.path("participantId").asLong() == 2)
                .hasSize(1);
    }

    @Test
    @DisplayName("상한을 넘어 버린 동작 정보도 연결 유지 신호로 센다")
    void droppedPoseKeepsConnectionAlive() {
        FakeWebSocketSession first = joined("s1", 1, "주영");
        for (int i = 0; i < 15; i++) {
            registry.relayPose("s1", pose()); // 0초: 15개 전달
        }
        clock.advance(Duration.ofMillis(900));
        registry.relayPose("s1", pose()); // 0.9초: 상한이라 버려짐

        clock.advance(Duration.ofMillis(29_600)); // 30.5초: 마지막 전달 기준이면 응답 없음(30초)을 넘음
        registry.sweep();

        assertThat(first.isOpen()).isTrue();
        assertThat(viewOf(1).connectionStatus()).isEqualTo(ConnectionStatus.CONNECTED);
    }

    @Test
    @DisplayName("카메라 오류(CAMERA_ERROR) 상태 참가자의 동작 정보는 전달하지 않는다")
    void cameraErrorPoseNotRelayed() {
        FakeWebSocketSession first = joined("s1", 1, "주영");
        FakeWebSocketSession second = joined("s2", 2, "지수");
        registry.updateStatus("s1", new StatusPayload(null, null, ProgressStatus.CAMERA_ERROR));

        registry.relayPose("s1", pose());

        assertThat(second.sentOf("PARTICIPANT_POSE")).isEmpty();
        assertThat(first.sentOf("ERROR")).as("보낸 사람에게도 오류를 보내지 않는다").isEmpty();
    }

    @Test
    @DisplayName("카메라 오류가 풀려 다른 상태가 되면 다시 전달한다")
    void poseRelayedAfterCameraRecovered() {
        joined("s1", 1, "주영");
        FakeWebSocketSession second = joined("s2", 2, "지수");
        registry.updateStatus("s1", new StatusPayload(null, null, ProgressStatus.CAMERA_ERROR));
        registry.relayPose("s1", pose());

        registry.updateStatus("s1", new StatusPayload(3L, "스쿼트", ProgressStatus.EXERCISING));
        registry.relayPose("s1", pose());

        assertThat(second.sentOf("PARTICIPANT_POSE")).hasSize(1);
    }

    @Test
    @DisplayName("JOIN 전에 동작 정보를 보내면 JOIN_REQUIRED를 받는다")
    void poseBeforeJoin() {
        FakeWebSocketSession session = new FakeWebSocketSession("s1");
        registry.connect(session);

        registry.relayPose("s1", pose());

        assertThat(session.last().path("payload").path("code").asString()).isEqualTo("JOIN_REQUIRED");
        assertThat(session.isOpen()).isTrue();
    }

    // ===== 응원(CHEER) — 이슈 #6 =====

    @Test
    @DisplayName("응원은 대상에게만 CHEER_RECEIVED로 가고, 보낸 사람은 CHEER_SENT를 받고, 다른 참가자는 아무것도 받지 않는다")
    void cheerDeliveredOnlyToTarget() {
        FakeWebSocketSession sender = joined("s1", 1, "주영");
        FakeWebSocketSession target = joined("s2", 2, "지수");
        FakeWebSocketSession other = joined("s3", 3, "민호");
        int otherBefore = other.sent().size();

        Optional<CheerRecordCommand> recorded = registry.cheer("s1", new CheerPayload(2L, "FIRE"));

        JsonNode received = target.last();
        assertThat(received.path("type").asString()).isEqualTo("CHEER_RECEIVED");
        assertThat(received.path("participantId").asLong()).as("보낸 사람").isEqualTo(1);
        assertThat(received.path("payload").path("targetParticipantId").asLong()).isEqualTo(2);
        assertThat(received.path("payload").path("reaction").asString()).isEqualTo("FIRE");

        JsonNode sent = sender.last();
        assertThat(sent.path("type").asString()).isEqualTo("CHEER_SENT");
        assertThat(sent.path("payload").path("targetParticipantId").asLong()).isEqualTo(2);

        assertThat(other.sent()).as("다른 참가자에게는 아무것도 가지 않는다").hasSize(otherBefore);
        assertThat(recorded).contains(new CheerRecordCommand(ROOM_ID, 1L, 2L, "FIRE", clock.instant()));
    }

    @Test
    @DisplayName("대상이 퇴장했으면 보낸 사람에게 CHEER_TARGET_UNAVAILABLE을 보내고 연결은 유지하며 저장하지 않는다")
    void cheerTargetLeft() {
        FakeWebSocketSession sender = joined("s1", 1, "주영");
        joined("s2", 2, "지수");
        registry.leave("s2");

        Optional<CheerRecordCommand> recorded = registry.cheer("s1", new CheerPayload(2L, "FIRE"));

        assertThat(sender.last().path("payload").path("code").asString()).isEqualTo("CHEER_TARGET_UNAVAILABLE");
        assertThat(sender.last().path("payload").path("closed").asBoolean()).isFalse();
        assertThat(sender.isOpen()).isTrue();
        assertThat(recorded).isEmpty();
    }

    @Test
    @DisplayName("대상이 연결 불안정이거나 방에 없는 참가자면 CHEER_TARGET_UNAVAILABLE을 받는다")
    void cheerTargetUnstableOrUnknown() {
        FakeWebSocketSession sender = joined("s1", 1, "주영");
        joined("s2", 2, "지수");
        registry.disconnected("s2");

        assertThat(registry.cheer("s1", new CheerPayload(2L, "FIRE"))).isEmpty();
        assertThat(sender.last().path("payload").path("code").asString()).isEqualTo("CHEER_TARGET_UNAVAILABLE");

        assertThat(registry.cheer("s1", new CheerPayload(99L, "FIRE"))).isEmpty();
        assertThat(sender.last().path("payload").path("code").asString()).isEqualTo("CHEER_TARGET_UNAVAILABLE");
    }

    @Test
    @DisplayName("자기 자신에게 응원하면 INVALID_MESSAGE를 받는다")
    void cheerSelf() {
        FakeWebSocketSession sender = joined("s1", 1, "주영");

        assertThat(registry.cheer("s1", new CheerPayload(1L, "FIRE"))).isEmpty();
        assertThat(sender.last().path("payload").path("code").asString()).isEqualTo("INVALID_MESSAGE");
    }

    @Test
    @DisplayName("같은 친구에게 1초 안에 다시 보낸 응원은 오류 없이 무시하고, 1초가 지나면 다시 전달한다")
    void cheerCooldownPerTarget() {
        FakeWebSocketSession sender = joined("s1", 1, "주영");
        FakeWebSocketSession target = joined("s2", 2, "지수");

        registry.cheer("s1", new CheerPayload(2L, "FIRE"));
        clock.advance(Duration.ofMillis(500));
        Optional<CheerRecordCommand> ignored = registry.cheer("s1", new CheerPayload(2L, "CLAP"));

        assertThat(ignored).isEmpty();
        assertThat(target.sentOf("CHEER_RECEIVED")).hasSize(1);
        assertThat(sender.sentOf("CHEER_SENT")).hasSize(1);
        assertThat(sender.sentOf("ERROR")).as("연타는 오류 없이 무시한다").isEmpty();

        clock.advance(Duration.ofMillis(500));
        assertThat(registry.cheer("s1", new CheerPayload(2L, "CLAP"))).isPresent();
        assertThat(target.sentOf("CHEER_RECEIVED")).hasSize(2);
    }

    @Test
    @DisplayName("연타 제한은 대상마다 따로 센다 (B에게 보낸 직후 C에게는 보낼 수 있다)")
    void cheerCooldownSeparatePerTarget() {
        joined("s1", 1, "주영");
        FakeWebSocketSession second = joined("s2", 2, "지수");
        FakeWebSocketSession third = joined("s3", 3, "민호");

        registry.cheer("s1", new CheerPayload(2L, "FIRE"));
        registry.cheer("s1", new CheerPayload(3L, "FIRE"));

        assertThat(second.sentOf("CHEER_RECEIVED")).hasSize(1);
        assertThat(third.sentOf("CHEER_RECEIVED")).hasSize(1);
    }

    @Test
    @DisplayName("대상이 없어 실패한 응원은 연타 제한에 넣지 않는다 (대상이 돌아오면 바로 보낼 수 있다)")
    void failedCheerDoesNotStartCooldown() {
        joined("s1", 1, "주영");
        joined("s2", 2, "지수");
        registry.disconnected("s2");
        registry.cheer("s1", new CheerPayload(2L, "FIRE"));

        FakeWebSocketSession rejoined = new FakeWebSocketSession("s2-new");
        registry.connect(rejoined);
        registry.join("s2-new", ticket(2, "지수"));

        assertThat(registry.cheer("s1", new CheerPayload(2L, "FIRE"))).isPresent();
        assertThat(rejoined.sentOf("CHEER_RECEIVED")).hasSize(1);
    }

    @Test
    @DisplayName("JOIN 전에 응원을 보내면 JOIN_REQUIRED를 받는다")
    void cheerBeforeJoin() {
        FakeWebSocketSession session = new FakeWebSocketSession("s1");
        registry.connect(session);

        assertThat(registry.cheer("s1", new CheerPayload(2L, "FIRE"))).isEmpty();
        assertThat(session.last().path("payload").path("code").asString()).isEqualTo("JOIN_REQUIRED");
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
