package com.team20.fitmate.domain.xp.service;

import com.team20.fitmate.domain.participant.dto.ParticipantCreateRequest;
import com.team20.fitmate.domain.participant.service.ParticipantService;
import com.team20.fitmate.domain.room.dto.RoomResponse;
import com.team20.fitmate.domain.room.service.RoomService;
import com.team20.fitmate.domain.routine.dto.RoutineResponse;
import com.team20.fitmate.domain.routine.entity.Goal;
import com.team20.fitmate.domain.routine.service.RoutineService;
import com.team20.fitmate.domain.workout.dto.WorkoutFinishRequest;
import com.team20.fitmate.domain.workout.dto.WorkoutResultResponse;
import com.team20.fitmate.domain.workout.service.WorkoutService;
import com.team20.fitmate.domain.xp.dto.TeamXpResponse;
import com.team20.fitmate.domain.xp.entity.XpStatus;
import com.team20.fitmate.domain.xp.repository.TeamXpLogRepository;
import com.team20.fitmate.global.exception.CustomException;
import com.team20.fitmate.global.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 경험치 산식이 정해지지 않은 기본 상태에서 임의 수치를 만들지 않는지, 결과 조회가 본인 기록만 돌려주는지 확인하는 테스트.
 */
@SpringBootTest
@Transactional
class TeamXpUndecidedTest {

    @Autowired
    private WorkoutService workoutService;

    @Autowired
    private TeamXpService teamXpService;

    @Autowired
    private ParticipantService participantService;

    @Autowired
    private RoutineService routineService;

    @Autowired
    private RoomService roomService;

    @Autowired
    private TeamXpLogRepository teamXpLogRepository;

    /** 운동방에 입장한 참가자를 만들고 토큰을 돌려준다 */
    private String participantInRoom(String nickname, RoomResponse room) {
        String token = participantService.create(new ParticipantCreateRequest(nickname, "CHARACTER_01")).clientToken();
        roomService.enter(token, room.roomId());
        return token;
    }

    @Test
    @DisplayName("산식이 정해지지 않으면 적립하지 않고, 결과와 팀 경험치 모두 수치를 비운다")
    void formulaUndecided() {
        RoomResponse room = roomService.getAll().getFirst();
        String token = participantInRoom("아현", room);
        RoutineResponse routine = routineService.createFirstRound(token, Goal.POSTURE);
        Long workoutId = workoutService.start(token, routine.routineId()).workoutId();
        workoutService.finish(token, workoutId, new WorkoutFinishRequest(List.of()));

        WorkoutResultResponse result = workoutService.getResult(token, workoutId);
        assertThat(result.teamXp().status()).isEqualTo(XpStatus.FORMULA_UNDECIDED);
        assertThat(result.teamXp().teamId()).isEqualTo(room.teamId());
        assertThat(result.teamXp().earnedXp()).isNull();
        assertThat(result.teamXp().totalXp()).isNull();
        assertThat(result.workout().records()).hasSize(2);
        assertThat(teamXpLogRepository.findByWorkoutId(workoutId)).isEmpty();

        TeamXpResponse team = teamXpService.getTeamXp(room.teamId());
        assertThat(team.formulaDecided()).isFalse();
        assertThat(team.totalXp()).isNull();
        assertThat(team.level()).isNull();
    }

    @Test
    @DisplayName("진행 중인 운동의 결과는 수행 기록이 비어 있고 경험치는 진행 중 상태다")
    void notFinished() {
        String token = participantInRoom("아현", roomService.getAll().getFirst());
        RoutineResponse routine = routineService.createFirstRound(token, Goal.POSTURE);
        Long workoutId = workoutService.start(token, routine.routineId()).workoutId();

        WorkoutResultResponse result = workoutService.getResult(token, workoutId);

        assertThat(result.workout().records()).isEmpty();
        assertThat(result.teamXp().status()).isEqualTo(XpStatus.NOT_FINISHED);
    }

    @Test
    @DisplayName("다른 참가자의 운동 결과는 조회할 수 없다")
    void othersResult() {
        String token = participantInRoom("아현", roomService.getAll().getFirst());
        RoutineResponse routine = routineService.createFirstRound(token, Goal.POSTURE);
        Long workoutId = workoutService.start(token, routine.routineId()).workoutId();
        String friend = participantInRoom("친구", roomService.getAll().getFirst());

        assertThatThrownBy(() -> workoutService.getResult(friend, workoutId))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getErrorCode())
                .isEqualTo(ErrorCode.WORKOUT_NOT_FOUND);
    }

    @Test
    @DisplayName("없는 팀의 경험치를 조회하면 거부한다")
    void unknownTeam() {
        assertThatThrownBy(() -> teamXpService.getTeamXp(999999L))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getErrorCode())
                .isEqualTo(ErrorCode.TEAM_NOT_FOUND);
    }
}
