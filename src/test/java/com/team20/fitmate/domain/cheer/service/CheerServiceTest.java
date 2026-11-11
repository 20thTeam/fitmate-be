package com.team20.fitmate.domain.cheer.service;

import com.team20.fitmate.domain.cheer.dto.CheerRecordCommand;
import com.team20.fitmate.domain.cheer.entity.Cheer;
import com.team20.fitmate.domain.cheer.repository.CheerRepository;
import com.team20.fitmate.domain.participant.dto.ParticipantCreateRequest;
import com.team20.fitmate.domain.participant.service.ParticipantService;
import com.team20.fitmate.domain.room.entity.Room;
import com.team20.fitmate.domain.room.entity.Team;
import com.team20.fitmate.domain.room.repository.RoomRepository;
import com.team20.fitmate.domain.room.repository.TeamRepository;
import com.team20.fitmate.domain.room.service.RoomService;
import com.team20.fitmate.global.exception.CustomException;
import com.team20.fitmate.global.exception.ErrorCode;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 전달된 응원을 저장하고, 같은 운동방 참가자끼리가 아니면 거부하는지 확인하는 테스트.
 */
@SpringBootTest
@Transactional
class CheerServiceTest {

    private static final Instant SENT_AT = Instant.parse("2026-11-11T05:00:00Z");

    @Autowired
    private CheerService cheerService;

    @Autowired
    private ObjectProvider<CheerRecorder> cheerRecorder;

    @Autowired
    private CheerRepository cheerRepository;

    @Autowired
    private ParticipantService participantService;

    @Autowired
    private RoomService roomService;

    @Autowired
    private RoomRepository roomRepository;

    @Autowired
    private TeamRepository teamRepository;

    @Autowired
    private EntityManager em;

    private Long roomId;

    private Long otherRoomId;

    @BeforeEach
    void setUp() {
        roomId = roomService.getAll().getFirst().roomId();
        Team otherTeam = teamRepository.save(Team.create("다른 팀"));
        otherRoomId = roomRepository.save(Room.create(otherTeam, "다른 방", 5)).getId();
    }

    /** 참가자를 만들고 (roomId가 있으면) 그 운동방에 입장시킨 뒤 식별값을 돌려준다 */
    private Long participant(String nickname, Long enterRoomId) {
        String token = participantService.create(new ParticipantCreateRequest(nickname, "CHARACTER_01")).clientToken();
        if (enterRoomId != null) {
            roomService.enter(token, enterRoomId);
        }
        return participantService.findByClientToken(token).getId();
    }

    /** 응원 저장이 지정한 오류로 거부되는지 확인한다 */
    private void assertRejected(CheerRecordCommand command, ErrorCode errorCode) {
        assertThatThrownBy(() -> cheerService.record(command))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getErrorCode())
                .isEqualTo(errorCode);
    }

    @Test
    @DisplayName("실시간 계층이 찾는 응원 저장 구현체가 이 서비스다")
    void registeredAsRecorder() {
        assertThat(cheerRecorder.getIfAvailable()).isSameAs(cheerService);
    }

    @Test
    @DisplayName("같은 운동방 참가자끼리 주고받은 응원을 저장한다")
    void saveSameRoom() {
        Long sender = participant("보낸이", roomId);
        Long receiver = participant("받은이", roomId);

        cheerService.record(new CheerRecordCommand(roomId, sender, receiver, "FIRE", SENT_AT));
        em.flush();
        em.clear();

        List<Cheer> cheers = cheerRepository.findByReceiverIdOrderByIdAsc(receiver);
        assertThat(cheers).hasSize(1);
        Cheer cheer = cheers.getFirst();
        assertThat(cheer.getRoom().getId()).isEqualTo(roomId);
        assertThat(cheer.getSender().getId()).isEqualTo(sender);
        assertThat(cheer.getReaction()).isEqualTo("FIRE");
        assertThat(cheer.getOccurredAt()).isEqualTo(SENT_AT);
        assertThat(cheer.getCreatedAt()).isNotNull();
    }

    @Test
    @DisplayName("받은 사람이 다른 운동방 참가자면 거부하고 저장하지 않는다")
    void rejectReceiverInOtherRoom() {
        Long sender = participant("보낸이", roomId);
        Long receiver = participant("다른방", otherRoomId);

        assertRejected(new CheerRecordCommand(roomId, sender, receiver, "FIRE", SENT_AT), ErrorCode.INVALID_CHEER);
        assertThat(cheerRepository.findByReceiverIdOrderByIdAsc(receiver)).isEmpty();
    }

    @Test
    @DisplayName("보낸 사람이 다른 운동방 참가자거나 아직 입장 전이면 거부한다")
    void rejectSenderNotInRoom() {
        Long receiver = participant("받은이", roomId);
        Long otherRoomSender = participant("다른방", otherRoomId);
        Long notEntered = participant("입장전", null);

        assertRejected(new CheerRecordCommand(roomId, otherRoomSender, receiver, "FIRE", SENT_AT),
                ErrorCode.INVALID_CHEER);
        assertRejected(new CheerRecordCommand(roomId, notEntered, receiver, "FIRE", SENT_AT),
                ErrorCode.INVALID_CHEER);
        assertThat(cheerRepository.findByReceiverIdOrderByIdAsc(receiver)).isEmpty();
    }

    @Test
    @DisplayName("둘 다 같은 방이어도 응원이 오간 운동방과 다르면 거부한다")
    void rejectBothInOtherRoom() {
        Long sender = participant("보낸이", otherRoomId);
        Long receiver = participant("받은이", otherRoomId);

        assertRejected(new CheerRecordCommand(roomId, sender, receiver, "FIRE", SENT_AT), ErrorCode.INVALID_CHEER);
    }

    @Test
    @DisplayName("자기 자신에게 보낸 응원과 빈 리액션은 거부한다")
    void rejectSelfAndBlankReaction() {
        Long sender = participant("보낸이", roomId);
        Long receiver = participant("받은이", roomId);

        assertRejected(new CheerRecordCommand(roomId, sender, sender, "FIRE", SENT_AT), ErrorCode.INVALID_CHEER);
        assertRejected(new CheerRecordCommand(roomId, sender, receiver, " ", SENT_AT), ErrorCode.INVALID_CHEER);
        assertRejected(new CheerRecordCommand(roomId, sender, receiver, "A".repeat(31), SENT_AT),
                ErrorCode.INVALID_CHEER);
    }

    @Test
    @DisplayName("없는 운동방이나 없는 참가자면 찾을 수 없음으로 거부한다")
    void rejectUnknown() {
        Long sender = participant("보낸이", roomId);
        Long receiver = participant("받은이", roomId);

        assertRejected(new CheerRecordCommand(-1L, sender, receiver, "FIRE", SENT_AT), ErrorCode.ROOM_NOT_FOUND);
        assertRejected(new CheerRecordCommand(roomId, sender, -1L, "FIRE", SENT_AT),
                ErrorCode.PARTICIPANT_NOT_FOUND);
    }
}
