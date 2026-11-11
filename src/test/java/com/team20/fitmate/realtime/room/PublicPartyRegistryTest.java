package com.team20.fitmate.realtime.room;

import com.team20.fitmate.domain.cheer.dto.CheerRecordCommand;
import com.team20.fitmate.realtime.message.CheerPayload;
import com.team20.fitmate.realtime.message.ParticipantView;
import com.team20.fitmate.realtime.message.StatusPayload;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 공개 파티(이슈 #11) 실시간 방 규칙. 친구 파티 운동방과 같은 경로를 쓰되 정원이 없고 서로 섞이지 않는다.
 */
class PublicPartyRegistryTest {

    private static final Long PARTY_ID = 1L;
    private static final SpaceKey PARTY = SpaceKey.publicParty(PARTY_ID);

    /** 테스트용 설정: JOIN 10초, 응답 30초, 재접속 30초, 동작 정보 2KB·초당 15개, 응원 연타 1초 */
    private final RealtimeProperties properties = new RealtimeProperties(
            Duration.ofSeconds(10), Duration.ofSeconds(30), Duration.ofSeconds(30), Duration.ofSeconds(5), 65536,
            2048, 15, Duration.ofSeconds(1));

    private RoomSessionRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new RoomSessionRegistry(properties, JsonMapper.builder().build(),
                new RoomSessionRegistryTest.MutableClock(Instant.parse("2026-11-11T13:30:00Z")));
    }

    /** 공개 파티에 입장시킨다 */
    private FakeWebSocketSession inParty(String sessionId, long participantId) {
        FakeWebSocketSession session = new FakeWebSocketSession(sessionId);
        registry.connect(session);
        registry.join(sessionId, JoinTicket.publicParty(PARTY_ID, participantId, "참가자" + participantId, "CHARACTER_01"));
        return session;
    }

    /** 친구 파티 운동방(정원 5)에 입장시킨다 */
    private FakeWebSocketSession inRoom(String sessionId, long roomId, long participantId) {
        FakeWebSocketSession session = new FakeWebSocketSession(sessionId);
        registry.connect(session);
        registry.join(sessionId, new JoinTicket(roomId, 5, participantId, "참가자" + participantId, "CHARACTER_01"));
        return session;
    }

    @Test
    @DisplayName("공개 파티는 정원이 없어 친구 파티 정원(5명)을 넘어 30명이 들어와도 모두 입장한다")
    void noCapacityLimit() {
        List<FakeWebSocketSession> sessions = new ArrayList<>();
        for (int i = 1; i <= 30; i++) {
            sessions.add(inParty("p" + i, i));
        }

        assertThat(registry.participants(PARTY)).hasSize(30);
        assertThat(registry.countParticipants(PARTY_ID)).isEqualTo(30);
        assertThat(sessions).allSatisfy(session -> {
            assertThat(session.sentOf("ERROR")).isEmpty();
            assertThat(session.isOpen()).isTrue();
        });
        JsonNode lastSnapshot = sessions.getLast().sentOf("ROOM_SNAPSHOT").getFirst();
        assertThat(lastSnapshot.path("payload").path("capacity").isNull()).isTrue();
        assertThat(lastSnapshot.path("payload").path("participants")).hasSize(30);
    }

    @Test
    @DisplayName("공개 파티 메시지 봉투에는 partyId가 들어가고 roomId는 null이다. 운동방 메시지는 반대다")
    void envelopeCarriesPartyId() {
        FakeWebSocketSession party = inParty("p1", 1);
        FakeWebSocketSession room = inRoom("r1", 1, 2);

        JsonNode partySnapshot = party.last();
        assertThat(partySnapshot.path("partyId").asLong()).isEqualTo(PARTY_ID);
        assertThat(partySnapshot.path("roomId").isNull()).isTrue();

        JsonNode roomSnapshot = room.last();
        assertThat(roomSnapshot.path("roomId").asLong()).isEqualTo(1);
        assertThat(roomSnapshot.path("partyId").isNull()).isTrue();
        assertThat(roomSnapshot.path("payload").path("capacity").asInt()).isEqualTo(5);
    }

    @Test
    @DisplayName("식별값이 같아도 공개 파티와 운동방은 별개 공간이라 입장·상태가 서로 넘어가지 않는다")
    void partyAndRoomAreSeparate() {
        FakeWebSocketSession partyMember = inParty("p1", 1);
        FakeWebSocketSession roomMember = inRoom("r1", 1, 2);

        registry.updateStatus("p1", new StatusPayload(null, "에어로빅", ProgressStatus.EXERCISING));

        assertThat(roomMember.sentOf("PARTICIPANT_STATUS")).isEmpty();
        assertThat(roomMember.sentOf("PARTICIPANT_JOINED")).isEmpty();
        assertThat(partyMember.sentOf("PARTICIPANT_JOINED")).isEmpty();
        assertThat(registry.participants(1L)).hasSize(1);
        assertThat(registry.participants(PARTY)).hasSize(1);
    }

    @Test
    @DisplayName("한 참가자가 시작·종료·퇴장해도 다른 참가자의 진행 상태는 바뀌지 않는다")
    void independentProgress() {
        inParty("p1", 1);
        FakeWebSocketSession second = inParty("p2", 2);
        inParty("p3", 3);

        registry.updateStatus("p1", new StatusPayload(null, "에어로빅", ProgressStatus.EXERCISING));
        registry.updateStatus("p1", new StatusPayload(null, "에어로빅", ProgressStatus.FINISHED));
        registry.leave("p1");

        assertThat(second.last().path("type").asString()).isEqualTo("PARTICIPANT_LEFT");
        assertThat(registry.participants(PARTY)).extracting(ParticipantView::progressStatus)
                .containsOnly(ProgressStatus.WAITING);
        assertThat(registry.countParticipants(PARTY_ID)).isEqualTo(2);
    }

    @Test
    @DisplayName("공개 파티에서 응원은 대상에게 전달되지만 저장할 응원은 돌려주지 않는다 (응원 저장은 친구 파티 기준)")
    void cheerInPartyNotRecorded() {
        inParty("p1", 1);
        FakeWebSocketSession target = inParty("p2", 2);

        Optional<CheerRecordCommand> recorded = registry.cheer("p1", new CheerPayload(2L, "FIRE"));

        assertThat(recorded).isEmpty();
        assertThat(target.last().path("type").asString()).isEqualTo("CHEER_RECEIVED");
        assertThat(target.last().path("partyId").asLong()).isEqualTo(PARTY_ID);
    }

    @Test
    @DisplayName("공개 파티에 아무도 없으면 인원은 0명이다")
    void emptyPartyCountsZero() {
        assertThat(registry.countParticipants(PARTY_ID)).isZero();
        inParty("p1", 1);
        registry.leave("p1");
        assertThat(registry.countParticipants(PARTY_ID)).isZero();
    }
}
