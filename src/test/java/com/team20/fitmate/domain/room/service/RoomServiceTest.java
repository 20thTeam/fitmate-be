package com.team20.fitmate.domain.room.service;

import com.team20.fitmate.domain.participant.dto.ParticipantCreateRequest;
import com.team20.fitmate.domain.participant.dto.ParticipantResponse;
import com.team20.fitmate.domain.participant.service.ParticipantService;
import com.team20.fitmate.domain.room.config.RoomProperties;
import com.team20.fitmate.domain.room.dto.RoomCreateRequest;
import com.team20.fitmate.domain.room.dto.RoomResponse;
import com.team20.fitmate.domain.room.init.RoomDataInitializer;
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
 * {@link RoomService}의 운동방 조회·만들기와 입장·나가기를 확인하는 테스트.
 * 지정 운동방과 시연용 운동방은 서버 시작 때 초기 데이터로 만들어진다.
 */
@SpringBootTest
@Transactional
class RoomServiceTest {

    @Autowired
    private RoomService roomService;

    @Autowired
    private ParticipantService participantService;

    @Autowired
    private RoomProperties roomProperties;

    @Autowired
    private RoomDataInitializer roomDataInitializer;

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
    @DisplayName("지정 운동방이 첫 번째로 팀과 정원 5명으로 준비되어 있다")
    void designatedRoomExists() {
        assertThat(designatedRoom.name()).isEqualTo("FITMATE 운동방");
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

    @Test
    @DisplayName("시연용 운동방이 지정 운동방과 함께 준비되고, 방마다 팀과 이름이 다르다")
    void seededRoomsHaveOwnTeams() {
        List<RoomResponse> rooms = roomService.getAll();

        assertThat(rooms).hasSize(1 + roomProperties.seedCount());
        assertThat(rooms).extracting(RoomResponse::name).contains("운동방 1", "운동방 " + roomProperties.seedCount());
        assertThat(rooms).extracting(RoomResponse::name).doesNotHaveDuplicates();
        assertThat(rooms).extracting(RoomResponse::teamId).doesNotHaveDuplicates();
        assertThat(rooms).allSatisfy(room -> assertThat(room.capacity()).isEqualTo(5));
    }

    @Test
    @DisplayName("서버를 다시 시작해도(초기화를 다시 돌려도) 운동방이 또 생기지 않는다")
    void seedingIsIdempotent() {
        int before = roomService.getAll().size();

        roomDataInitializer.run(null);

        assertThat(roomService.getAll()).hasSize(before);
    }

    @Test
    @DisplayName("운동방을 만들면 새 팀과 함께 목록에 나오고, 그 방에 입장할 수 있다")
    void createRoom() {
        String token = createParticipant("민지");

        RoomResponse created = roomService.create(token, new RoomCreateRequest("  민지의 운동방  "));

        assertThat(created.name()).isEqualTo("민지의 운동방");
        assertThat(created.capacity()).isEqualTo(5);
        assertThat(created.teamName()).isEqualTo("민지의 운동방 팀");
        assertThat(roomService.getAll()).extracting(RoomResponse::teamId)
                .doesNotHaveDuplicates()
                .contains(created.teamId());
        assertThat(roomService.enter(token, created.roomId()).roomId()).isEqualTo(created.roomId());
    }

    @Test
    @DisplayName("운동방 수가 상한이면 더 만들 수 없다")
    void createRoomOverLimit() {
        String token = createParticipant("민지");
        int remaining = roomProperties.maxCount() - roomService.getAll().size();
        IntStream.range(0, remaining).forEach(i -> roomService.create(token, new RoomCreateRequest("방" + i)));

        assertThatThrownBy(() -> roomService.create(token, new RoomCreateRequest("하나 더")))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getErrorCode())
                .isEqualTo(ErrorCode.ROOM_LIMIT_EXCEEDED);
    }

    @Test
    @DisplayName("없는 토큰으로는 운동방을 만들 수 없다")
    void createRoomWithUnknownToken() {
        assertThatThrownBy(() -> roomService.create("없는-토큰", new RoomCreateRequest("방")))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getErrorCode())
                .isEqualTo(ErrorCode.PARTICIPANT_NOT_FOUND);
    }

    @Test
    @DisplayName("운동방에서 나가면 참가자의 roomId가 null이 된다")
    void leave() {
        String token = createParticipant("아현");
        roomService.enter(token, designatedRoom.roomId());

        roomService.leave(token, designatedRoom.roomId());

        assertThat(participantService.getByClientToken(token).roomId()).isNull();
    }

    @Test
    @DisplayName("다른 방에 들어가 있으면 나가기를 해도 그 방 입장 기록을 지우지 않는다")
    void leaveOtherRoomKeepsCurrentRoom() {
        String token = createParticipant("아현");
        RoomResponse other = roomService.getAll().get(1);
        roomService.enter(token, other.roomId());

        roomService.leave(token, designatedRoom.roomId());

        assertThat(participantService.getByClientToken(token).roomId()).isEqualTo(other.roomId());
    }

    @Test
    @DisplayName("입장 전에 나가기를 해도 성공한다")
    void leaveWithoutEntering() {
        String token = createParticipant("아현");

        roomService.leave(token, designatedRoom.roomId());

        assertThat(participantService.getByClientToken(token).roomId()).isNull();
    }

    @Test
    @DisplayName("없는 운동방에서 나가면 ROOM_NOT_FOUND 예외가 난다")
    void leaveUnknownRoom() {
        String token = createParticipant("아현");

        assertThatThrownBy(() -> roomService.leave(token, 999999L))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getErrorCode())
                .isEqualTo(ErrorCode.ROOM_NOT_FOUND);
    }
}
