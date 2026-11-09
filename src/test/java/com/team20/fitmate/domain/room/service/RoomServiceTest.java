package com.team20.fitmate.domain.room.service;

import com.team20.fitmate.domain.participant.dto.ParticipantCreateRequest;
import com.team20.fitmate.domain.participant.dto.ParticipantResponse;
import com.team20.fitmate.domain.participant.service.ParticipantService;
import com.team20.fitmate.domain.room.dto.RoomResponse;
import com.team20.fitmate.global.exception.CustomException;
import com.team20.fitmate.global.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link RoomService}의 지정 운동방 조회와 입장을 확인하는 테스트.
 * 지정 운동방은 서버 시작 때 초기 데이터로 하나 만들어진다.
 */
@SpringBootTest
@Transactional
class RoomServiceTest {

    @Autowired
    private RoomService roomService;

    @Autowired
    private ParticipantService participantService;

    private RoomResponse designatedRoom;

    @BeforeEach
    void setUp() {
        designatedRoom = roomService.getAll().getFirst();
    }

    /** 테스트용 참가자를 만들고 재방문 토큰을 돌려준다 */
    private String createParticipant(String nickname) {
        return participantService.create(new ParticipantCreateRequest(nickname, "CHARACTER_01")).clientToken();
    }

    @Test
    @DisplayName("지정 운동방 하나가 팀과 정원 5명으로 준비되어 있다")
    void designatedRoomExists() {
        List<RoomResponse> rooms = roomService.getAll();

        assertThat(rooms).hasSize(1);
        assertThat(designatedRoom.capacity()).isEqualTo(5);
        assertThat(designatedRoom.teamId()).isNotNull();
        assertThat(designatedRoom.teamName()).isNotBlank();
    }

    @Test
    @DisplayName("입장하면 참가자가 운동방에 연결된다")
    void enter() {
        String token = createParticipant("아현");

        RoomResponse response = roomService.enter(token, designatedRoom.roomId());

        assertThat(response.roomId()).isEqualTo(designatedRoom.roomId());
        ParticipantResponse me = participantService.getByClientToken(token);
        assertThat(me.roomId()).isEqualTo(designatedRoom.roomId());
    }

    @Test
    @DisplayName("입장 전 참가자의 운동방은 null이다")
    void notEnteredYet() {
        String token = createParticipant("아현");

        assertThat(participantService.getByClientToken(token).roomId()).isNull();
    }

    @Test
    @DisplayName("같은 방에 다시 입장해도 성공한다")
    void enterTwice() {
        String token = createParticipant("아현");

        roomService.enter(token, designatedRoom.roomId());
        RoomResponse again = roomService.enter(token, designatedRoom.roomId());

        assertThat(again.roomId()).isEqualTo(designatedRoom.roomId());
    }

    @Test
    @DisplayName("같은 닉네임 참가자들도 각자 입장하고 서로 다른 참가자로 남는다")
    void sameNicknameParticipants() {
        String first = createParticipant("아현");
        String second = createParticipant("아현");

        roomService.enter(first, designatedRoom.roomId());
        roomService.enter(second, designatedRoom.roomId());

        assertThat(participantService.getByClientToken(first).participantId())
                .isNotEqualTo(participantService.getByClientToken(second).participantId());
    }

    @Test
    @DisplayName("DB 연결 기록이 정원보다 많아도 입장을 막지 않는다 (정원은 실시간 연결 수로 판단)")
    void enterBeyondCapacityInDb() {
        IntStream.range(0, designatedRoom.capacity())
                .forEach(i -> roomService.enter(createParticipant("참가자" + i), designatedRoom.roomId()));

        RoomResponse response = roomService.enter(createParticipant("늦게 온 참가자"), designatedRoom.roomId());

        assertThat(response.roomId()).isEqualTo(designatedRoom.roomId());
    }

    @Test
    @DisplayName("없는 운동방에 입장하면 ROOM_NOT_FOUND 예외가 난다")
    void enterUnknownRoom() {
        String token = createParticipant("아현");

        assertThatThrownBy(() -> roomService.enter(token, 999999L))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getErrorCode())
                .isEqualTo(ErrorCode.ROOM_NOT_FOUND);
    }

    @Test
    @DisplayName("없는 토큰으로 입장하면 PARTICIPANT_NOT_FOUND 예외가 난다")
    void enterWithUnknownToken() {
        assertThatThrownBy(() -> roomService.enter("없는-토큰", designatedRoom.roomId()))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getErrorCode())
                .isEqualTo(ErrorCode.PARTICIPANT_NOT_FOUND);
    }
}
