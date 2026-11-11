package com.team20.fitmate.realtime.room;

import com.team20.fitmate.realtime.message.ParticipantView;
import com.team20.fitmate.realtime.message.PosePayload;
import com.team20.fitmate.realtime.message.StatusPayload;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 이슈 #7(친구와 관계없이 내 루틴 진행)과 #9(2회차 재입장)의 실시간 규칙을 고정하는 테스트.
 * <ul>
 *     <li>친구의 시작·종료·퇴장·연결 끊김이 내 진행 상태와 연결 상태를 바꾸지 않는다.</li>
 *     <li>재접속하면 끊기기 전 상태를 그대로 복원하고, 끊긴 동안의 상태·동작 정보는 만들지 않는다.</li>
 *     <li>내 연결 문제와 친구 연결 문제가 서로 다른 신호로 구분된다.</li>
 *     <li>2회차로 다시 들어와 새 루틴의 운동을 보내면 친구 화면의 내 상태가 새 운동으로 바뀐다.</li>
 * </ul>
 */
class RoomIndependenceTest {

    private static final Long ROOM_ID = 1L;
    private static final int CAPACITY = 5;
    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    /** 1회차 루틴의 첫 운동(루틴 운동 식별값) */
    private static final long ROUND1_SQUAT = 11L;

    /** 1회차 루틴의 마지막 운동 */
    private static final long ROUND1_PLANK = 12L;

    /** 2회차 추천 루틴의 첫 운동. 새 루틴이라 식별값이 다르다 */
    private static final long ROUND2_SQUAT = 21L;

    /** 테스트용 설정: JOIN 10초, 응답 30초, 재접속 30초, 동작 정보 2KB·초당 15개, 응원 연타 1초 */
    private final RealtimeProperties properties = new RealtimeProperties(
            Duration.ofSeconds(10), Duration.ofSeconds(30), Duration.ofSeconds(30), Duration.ofSeconds(5), 65536,
            2048, 15, Duration.ofSeconds(1));

    private RoomSessionRegistryTest.MutableClock clock;
    private RoomSessionRegistry registry;

    @BeforeEach
    void setUp() {
        clock = new RoomSessionRegistryTest.MutableClock(Instant.parse("2026-11-11T12:00:00Z"));
        registry = new RoomSessionRegistry(properties, JsonMapper.builder().build(), clock);
    }

    /** 연결을 만들고 참가자로 입장시킨다 */
    private FakeWebSocketSession joined(String sessionId, long participantId) {
        FakeWebSocketSession session = new FakeWebSocketSession(sessionId);
        registry.connect(session);
        registry.join(sessionId, new JoinTicket(ROOM_ID, CAPACITY, participantId, "참가자" + participantId, "CHARACTER_01"));
        return session;
    }

    /** 레지스트리에 있는 참가자 한 명의 현재 상태 */
    private ParticipantView viewOf(long participantId) {
        return registry.participants(ROOM_ID).stream()
                .filter(view -> view.participantId() == participantId)
                .findFirst().orElseThrow();
    }

    /** 운동 진행 상태를 보낸다 */
    private void status(String sessionId, Long exerciseId, String name, ProgressStatus progressStatus) {
        registry.updateStatus(sessionId, new StatusPayload(exerciseId, name, progressStatus));
    }

    /** 관절 하나짜리 동작 정보 */
    private static PosePayload pose() {
        return PosePayload.from(MAPPER.readTree("{\"landmarks\":[[0.1,0.2,0.3]]}"));
    }

    /** 특정 참가자가 주인공인 메시지만 고른다 */
    private static List<JsonNode> aboutParticipant(FakeWebSocketSession session, String type, long participantId) {
        return session.sentOf(type).stream()
                .filter(node -> node.path("participantId").asLong() == participantId)
                .toList();
    }

    @Test
    @DisplayName("참가자마다 다른 시점에 시작하고 끝내도 각자의 진행 상태가 따로 유지된다")
    void eachParticipantStartsAndFinishesIndependently() {
        joined("s1", 1);
        joined("s2", 2);
        joined("s3", 3);

        status("s1", ROUND1_SQUAT, "스쿼트", ProgressStatus.EXERCISING);
        clock.advance(Duration.ofSeconds(20));
        status("s2", ROUND1_SQUAT, "스쿼트", ProgressStatus.EXERCISING);
        clock.advance(Duration.ofSeconds(40));
        status("s1", null, null, ProgressStatus.FINISHED);

        assertThat(viewOf(1).progressStatus()).isEqualTo(ProgressStatus.FINISHED);
        assertThat(viewOf(2).progressStatus()).isEqualTo(ProgressStatus.EXERCISING);
        assertThat(viewOf(2).exerciseId()).isEqualTo(ROUND1_SQUAT);
        assertThat(viewOf(3).progressStatus()).isEqualTo(ProgressStatus.WAITING);
    }

    @Test
    @DisplayName("친구가 먼저 끝내도(FINISHED) 내 상태는 그대로이고 나는 다음 운동으로 계속 진행한다")
    void friendFinishingDoesNotChangeMine() {
        FakeWebSocketSession me = joined("s1", 1);
        FakeWebSocketSession friend = joined("s2", 2);
        status("s1", ROUND1_SQUAT, "스쿼트", ProgressStatus.EXERCISING);
        status("s2", ROUND1_SQUAT, "스쿼트", ProgressStatus.EXERCISING);

        status("s2", null, null, ProgressStatus.FINISHED);

        assertThat(viewOf(1).progressStatus()).isEqualTo(ProgressStatus.EXERCISING);
        assertThat(viewOf(1).exerciseId()).isEqualTo(ROUND1_SQUAT);
        assertThat(me.sentOf("ERROR")).isEmpty();

        status("s1", ROUND1_PLANK, "플랭크", ProgressStatus.EXERCISING);

        assertThat(viewOf(1).exerciseId()).isEqualTo(ROUND1_PLANK);
        assertThat(friend.last().path("type").asString()).isEqualTo("PARTICIPANT_STATUS");
        assertThat(friend.last().path("payload").path("exerciseId").asLong()).isEqualTo(ROUND1_PLANK);
    }

    @Test
    @DisplayName("친구가 퇴장해도 내 상태는 그대로이고 상태·동작 정보를 계속 보낼 수 있다")
    void friendLeavingDoesNotChangeMine() {
        FakeWebSocketSession me = joined("s1", 1);
        joined("s2", 2);
        FakeWebSocketSession third = joined("s3", 3);
        status("s1", ROUND1_SQUAT, "스쿼트", ProgressStatus.EXERCISING);

        registry.leave("s2");

        assertThat(viewOf(1).progressStatus()).isEqualTo(ProgressStatus.EXERCISING);
        assertThat(viewOf(1).connectionStatus()).isEqualTo(ConnectionStatus.CONNECTED);
        assertThat(me.isOpen()).isTrue();

        status("s1", ROUND1_SQUAT, "스쿼트", ProgressStatus.RESTING);
        registry.relayPose("s1", pose());

        assertThat(me.sentOf("ERROR")).isEmpty();
        assertThat(aboutParticipant(third, "PARTICIPANT_STATUS", 1).getLast()
                .path("payload").path("progressStatus").asString()).isEqualTo("RESTING");
        assertThat(aboutParticipant(third, "PARTICIPANT_POSE", 1)).hasSize(1);
    }

    @Test
    @DisplayName("친구 연결이 끊기거나 재접속 대기 시간이 지나 퇴장돼도 내 진행·연결 상태는 바뀌지 않는다")
    void friendDisconnectDoesNotChangeMine() {
        FakeWebSocketSession me = joined("s1", 1);
        joined("s2", 2);
        status("s1", ROUND1_SQUAT, "스쿼트", ProgressStatus.EXERCISING);

        registry.disconnected("s2");

        assertThat(viewOf(2).connectionStatus()).isEqualTo(ConnectionStatus.UNSTABLE);
        assertThat(viewOf(1).connectionStatus()).isEqualTo(ConnectionStatus.CONNECTED);
        assertThat(viewOf(1).progressStatus()).isEqualTo(ProgressStatus.EXERCISING);

        clock.advance(Duration.ofSeconds(31));
        registry.ping("s1");
        registry.sweep();

        assertThat(registry.participants(ROOM_ID)).extracting(ParticipantView::participantId).containsExactly(1L);
        assertThat(viewOf(1).connectionStatus()).isEqualTo(ConnectionStatus.CONNECTED);
        assertThat(viewOf(1).progressStatus()).isEqualTo(ProgressStatus.EXERCISING);
        assertThat(me.isOpen()).isTrue();
        assertThat(me.sentOf("ERROR")).isEmpty();
    }

    @Test
    @DisplayName("친구 연결 문제는 CONNECTION_STATUS로만 오고, 내 연결이 끊긴 것은 내 연결 닫힘으로만 드러난다")
    void myConnectionProblemIsDistinguishedFromFriends() {
        FakeWebSocketSession me = joined("s1", 1);
        FakeWebSocketSession friend = joined("s2", 2);

        registry.disconnected("s2");

        // 친구 문제: 나에게는 친구가 주인공인 CONNECTION_STATUS가 오고, 내 연결은 열려 있다
        assertThat(me.last().path("type").asString()).isEqualTo("CONNECTION_STATUS");
        assertThat(me.last().path("participantId").asLong()).isEqualTo(2);
        assertThat(me.isOpen()).isTrue();

        // 내 문제: 30초 동안 메시지가 없으면 내 연결이 HEARTBEAT_TIMEOUT으로 닫힌다. 나를 주인공으로 한 CONNECTION_STATUS는 나에게 오지 않는다
        clock.advance(Duration.ofSeconds(30));
        registry.sweep();

        assertThat(me.closeStatus()).isEqualTo(RoomSessionRegistry.HEARTBEAT_TIMEOUT);
        assertThat(aboutParticipant(me, "CONNECTION_STATUS", 1)).isEmpty();
        assertThat(friend.closeStatus()).isNull();
    }

    @Test
    @DisplayName("처음 입장한 스냅샷은 reconnected=false, 끊긴 뒤 다시 들어온 스냅샷은 reconnected=true다")
    void snapshotTellsWhetherReconnected() {
        FakeWebSocketSession first = joined("s1", 1);
        assertThat(first.last().path("payload").path("reconnected").asBoolean()).isFalse();

        registry.disconnected("s1");
        FakeWebSocketSession again = joined("s1-again", 1);

        JsonNode snapshot = again.last();
        assertThat(snapshot.path("type").asString()).isEqualTo("ROOM_SNAPSHOT");
        assertThat(snapshot.path("payload").path("reconnected").asBoolean()).isTrue();
    }

    @Test
    @DisplayName("재접속하면 끊기기 전 진행 상태를 복원하고, 끊긴 동안의 상태 변경·동작 정보는 친구에게 만들어 보내지 않는다")
    void reconnectRestoresWithoutInventingRecords() {
        joined("s1", 1);
        FakeWebSocketSession friend = joined("s2", 2);
        status("s1", ROUND1_SQUAT, "스쿼트", ProgressStatus.EXERCISING);
        registry.relayPose("s1", pose());
        int statusBefore = aboutParticipant(friend, "PARTICIPANT_STATUS", 1).size();
        int poseBefore = aboutParticipant(friend, "PARTICIPANT_POSE", 1).size();

        registry.disconnected("s1");
        clock.advance(Duration.ofSeconds(20));
        registry.ping("s2");
        registry.sweep();
        FakeWebSocketSession again = joined("s1-again", 1);

        JsonNode me = again.last().path("payload").path("participants").get(0);
        assertThat(me.path("participantId").asLong()).isEqualTo(1);
        assertThat(me.path("exerciseId").asLong()).isEqualTo(ROUND1_SQUAT);
        assertThat(me.path("progressStatus").asString()).isEqualTo("EXERCISING");
        assertThat(me.path("connectionStatus").asString()).isEqualTo("CONNECTED");
        assertThat(me.has("landmarks")).isFalse();

        assertThat(aboutParticipant(friend, "PARTICIPANT_STATUS", 1)).hasSize(statusBefore);
        assertThat(aboutParticipant(friend, "PARTICIPANT_POSE", 1)).hasSize(poseBefore);
        assertThat(aboutParticipant(friend, "CONNECTION_STATUS", 1)).extracting(
                node -> node.path("payload").path("connectionStatus").asString()).containsExactly("UNSTABLE", "CONNECTED");
    }

    @Test
    @DisplayName("다른 창으로 밀려난 예전 연결로 늦게 온 상태·동작 정보는 전달되지 않는다")
    void replacedConnectionMessagesAreIgnored() {
        FakeWebSocketSession old = joined("s1", 1);
        FakeWebSocketSession friend = joined("s2", 2);
        status("s1", ROUND1_SQUAT, "스쿼트", ProgressStatus.EXERCISING);
        joined("s1-new", 1);
        int friendBefore = friend.sent().size();

        status("s1", ROUND1_PLANK, "플랭크", ProgressStatus.FINISHED);
        registry.relayPose("s1", pose());

        assertThat(old.closeStatus()).isNotNull();
        assertThat(friend.sent()).hasSize(friendBefore);
        assertThat(viewOf(1).progressStatus()).isEqualTo(ProgressStatus.EXERCISING);
        assertThat(viewOf(1).exerciseId()).isEqualTo(ROUND1_SQUAT);
    }

    @Test
    @DisplayName("1회차를 끝낸 뒤 같은 연결에서 2회차 운동을 보내면 친구 화면의 내 상태가 새 루틴의 운동으로 바뀐다")
    void secondRoundOnSameConnectionUpdatesStatus() {
        joined("s1", 1);
        FakeWebSocketSession friend = joined("s2", 2);
        status("s1", ROUND1_PLANK, "플랭크", ProgressStatus.EXERCISING);
        status("s1", null, null, ProgressStatus.FINISHED);

        status("s1", ROUND2_SQUAT, "스쿼트", ProgressStatus.WAITING);
        status("s1", ROUND2_SQUAT, "스쿼트", ProgressStatus.EXERCISING);

        assertThat(viewOf(1).exerciseId()).isEqualTo(ROUND2_SQUAT);
        assertThat(viewOf(1).progressStatus()).isEqualTo(ProgressStatus.EXERCISING);
        assertThat(aboutParticipant(friend, "PARTICIPANT_STATUS", 1)).extracting(
                node -> node.path("payload").path("progressStatus").asString())
                .containsExactly("EXERCISING", "FINISHED", "WAITING", "EXERCISING");
        assertThat(friend.last().path("payload").path("exerciseId").asLong()).isEqualTo(ROUND2_SQUAT);
    }

    @Test
    @DisplayName("1회차 종료 후 결과 화면을 거쳐 새 연결로 다시 들어오면 종료 상태로 복원되고, 2회차 운동을 보내면 새 운동으로 바뀐다")
    void secondRoundOnNewConnectionUpdatesStatus() {
        FakeWebSocketSession round1 = joined("s1", 1);
        FakeWebSocketSession friend = joined("s2", 2);
        status("s1", ROUND1_PLANK, "플랭크", ProgressStatus.FINISHED);

        // 결과 화면으로 이동하며 연결을 닫았다가 2회차 입장으로 새 연결을 연다
        registry.disconnected("s1");
        FakeWebSocketSession round2 = joined("s1-round2", 1);

        JsonNode restored = round2.last().path("payload").path("participants").get(0);
        assertThat(round2.last().path("payload").path("reconnected").asBoolean()).isTrue();
        assertThat(restored.path("progressStatus").asString()).isEqualTo("FINISHED");
        assertThat(round1.closeStatus()).isNull(); // 서버가 닫은 것이 아니라 클라이언트가 닫은 연결이다

        status("s1-round2", ROUND2_SQUAT, "스쿼트", ProgressStatus.EXERCISING);

        assertThat(viewOf(1).exerciseId()).isEqualTo(ROUND2_SQUAT);
        assertThat(viewOf(1).progressStatus()).isEqualTo(ProgressStatus.EXERCISING);
        JsonNode relayed = friend.last();
        assertThat(relayed.path("type").asString()).isEqualTo("PARTICIPANT_STATUS");
        assertThat(relayed.path("participantId").asLong()).isEqualTo(1);
        assertThat(relayed.path("payload").path("exerciseId").asLong()).isEqualTo(ROUND2_SQUAT);
        assertThat(viewOf(2).progressStatus()).isEqualTo(ProgressStatus.WAITING);
    }
}
