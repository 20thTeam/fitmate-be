package com.team20.fitmate.domain.xp.service;

import com.team20.fitmate.domain.participant.dto.ParticipantCreateRequest;
import com.team20.fitmate.domain.participant.service.ParticipantService;
import com.team20.fitmate.domain.room.dto.RoomResponse;
import com.team20.fitmate.domain.room.service.RoomService;
import com.team20.fitmate.domain.routine.dto.RoutineResponse;
import com.team20.fitmate.domain.routine.entity.Goal;
import com.team20.fitmate.domain.routine.service.RoutineService;
import com.team20.fitmate.domain.workout.dto.ExerciseRecordRequest;
import com.team20.fitmate.domain.workout.dto.WorkoutFinishRequest;
import com.team20.fitmate.domain.workout.dto.WorkoutResultResponse;
import com.team20.fitmate.domain.workout.entity.MeasurementStatus;
import com.team20.fitmate.domain.workout.entity.Workout;
import com.team20.fitmate.domain.workout.repository.ExerciseRecordRepository;
import com.team20.fitmate.domain.workout.repository.WorkoutRepository;
import com.team20.fitmate.domain.workout.service.WorkoutService;
import com.team20.fitmate.domain.xp.entity.TeamLevel;
import com.team20.fitmate.domain.xp.entity.TeamXpLog;
import com.team20.fitmate.domain.xp.entity.XpStatus;
import com.team20.fitmate.domain.xp.repository.TeamXpLogRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 팀 경험치 적립(PM 확정 산식), 하루 최대치, 레벨 상승, 중복 적립 방지를 확인하는 테스트.
 * 루틴은 자세 개선 목표(스쿼트 10회 × 3세트, 플랭크 20초 × 3세트)로 만든다.
 */
@SpringBootTest
@Transactional
class TeamXpServiceTest {

    @Autowired
    private TeamXpService teamXpService;

    @Autowired
    private WorkoutService workoutService;

    @Autowired
    private ParticipantService participantService;

    @Autowired
    private RoutineService routineService;

    @Autowired
    private RoomService roomService;

    @Autowired
    private WorkoutRepository workoutRepository;

    @Autowired
    private ExerciseRecordRepository exerciseRecordRepository;

    @Autowired
    private TeamXpLogRepository teamXpLogRepository;

    @Autowired
    private EntityManager em;

    private RoomResponse room;

    @BeforeEach
    void setUp() {
        room = roomService.getAll().getFirst();
    }

    /** 참가자를 만들고 (선택) 운동방에 입장시킨 뒤 토큰을 돌려준다 */
    private String participant(String nickname, boolean enterRoom) {
        String token = participantService.create(new ParticipantCreateRequest(nickname, "CHARACTER_01")).clientToken();
        if (enterRoom) {
            roomService.enter(token, room.roomId());
        }
        return token;
    }

    /** 자세 개선 루틴으로 운동을 시작해 주어진 수행 기록으로 종료한 뒤 운동 기록 식별값을 돌려준다 */
    private Long finishWorkout(String token, Integer squatValidCount, Integer plankSeconds) {
        RoutineResponse routine = routineService.createFirstRound(token, Goal.POSTURE);
        Long workoutId = workoutService.start(token, routine.routineId()).workoutId();
        List<ExerciseRecordRequest> records = new java.util.ArrayList<>();
        if (squatValidCount != null) {
            records.add(new ExerciseRecordRequest(routine.exercises().get(0).routineExerciseId(),
                    MeasurementStatus.MEASURED, squatValidCount, 0, 0, 60, 3, null));
        }
        if (plankSeconds != null) {
            records.add(new ExerciseRecordRequest(routine.exercises().get(1).routineExerciseId(),
                    MeasurementStatus.MEASURED, null, null, null, plankSeconds, 3, null));
        }
        workoutService.finish(token, workoutId, new WorkoutFinishRequest(records));
        return workoutId;
    }

    /** 스쿼트 유효 횟수만 보내고 종료한다 (플랭크는 측정 못 함) */
    private Long finishWorkout(String token, int squatValidCount) {
        return finishWorkout(token, squatValidCount, null);
    }

    /** 루틴 목표를 모두 채우고 종료한다: 스쿼트 30XP + 플랭크 15XP + 보너스 20XP = 65XP */
    private Long finishFullRoutine(String token) {
        return finishWorkout(token, 30, 60);
    }

    /** 지금 팀 누적 경험치 */
    private int teamTotalXp() {
        return teamXpService.getTeamXp(room.teamId()).totalXp();
    }

    @Test
    @DisplayName("운동방에서 운동을 끝내면 팀 경험치가 적립되고 결과에 적립·누적 경험치가 나온다")
    void accrueOnFinish() {
        int before = teamTotalXp();
        String token = participant("아현", true);

        Long workoutId = finishWorkout(token, 10);

        WorkoutResultResponse result = workoutService.getResult(token, workoutId);
        assertThat(result.teamXp().status()).isEqualTo(XpStatus.ACCRUED);
        assertThat(result.teamXp().teamId()).isEqualTo(room.teamId());
        assertThat(result.teamXp().earnedXp()).isEqualTo(10);
        assertThat(result.teamXp().totalXp()).isEqualTo(before + 10);
        assertThat(result.teamXp().level()).isEqualTo(TeamLevel.of(before + 10).level());
        assertThat(teamTotalXp()).isEqualTo(before + 10);
    }

    @Test
    @DisplayName("같은 운동 기록으로 다시 적립을 시도해도 한 번만 적립된다")
    void accrueOnlyOnce() {
        String token = participant("아현", true);
        Long workoutId = finishWorkout(token, 10);
        int afterFirst = teamTotalXp();
        Workout workout = workoutRepository.findById(workoutId).orElseThrow();

        teamXpService.accrue(workout, exerciseRecordRepository.findByWorkoutIdOrderByIdAsc(workoutId));

        assertThat(teamTotalXp()).isEqualTo(afterFirst);
        assertThat(teamXpLogRepository.findAll()).filteredOn(log -> log.getWorkout().getId().equals(workoutId))
                .hasSize(1);
    }

    @Test
    @DisplayName("서비스 검사를 거치지 않아도 DB 제약이 같은 운동 기록의 두 번째 적립을 막는다")
    void uniqueConstraint() {
        String token = participant("아현", true);
        Long workoutId = finishWorkout(token, 10);
        TeamXpLog first = teamXpLogRepository.findByWorkoutId(workoutId).orElseThrow();

        assertThatThrownBy(() -> {
            teamXpLogRepository.save(TeamXpLog.of(first.getTeam(), first.getWorkout(), 5));
            em.flush();
        }).isInstanceOfAny(DataIntegrityViolationException.class,
                org.hibernate.exception.ConstraintViolationException.class);
    }

    @Test
    @DisplayName("팀원 둘이 운동하면 팀 누적에 둘 다 더해지고, 각자 결과에는 자기 적립 경험치만 나온다")
    void teammatesDoNotMix() {
        int before = teamTotalXp();
        String me = participant("아현", true);
        String friend = participant("친구", true);

        Long myWorkout = finishWorkout(me, 10);
        Long friendWorkout = finishWorkout(friend, 7);

        assertThat(workoutService.getResult(me, myWorkout).teamXp().earnedXp()).isEqualTo(10);
        assertThat(workoutService.getResult(friend, friendWorkout).teamXp().earnedXp()).isEqualTo(7);
        assertThat(workoutService.getResult(me, myWorkout).workout().records().getFirst().validCount()).isEqualTo(10);
        assertThat(teamTotalXp()).isEqualTo(before + 17);
    }

    @Test
    @DisplayName("운동방에 입장하지 않고 운동하면 적립할 팀이 없다")
    void noTeam() {
        String token = participant("아현", false);

        Long workoutId = finishWorkout(token, 10);

        assertThat(workoutService.getResult(token, workoutId).teamXp().status()).isEqualTo(XpStatus.NO_TEAM);
        assertThat(teamXpLogRepository.findByWorkoutId(workoutId)).isEmpty();
    }

    @Test
    @DisplayName("루틴 목표를 모두 채우면 보너스 20XP까지 65XP가 적립된다")
    void fullRoutine() {
        String token = participant("아현", true);

        Long workoutId = finishFullRoutine(token);

        assertThat(workoutService.getResult(token, workoutId).teamXp().earnedXp()).isEqualTo(65);
    }

    @Test
    @DisplayName("참가자 1명은 하루 최대 100XP까지만 적립되고, 넘으면 0XP로 기록된다")
    void dailyLimit() {
        int before = teamTotalXp();
        String token = participant("아현", true);

        Long first = finishFullRoutine(token);
        Long second = finishFullRoutine(token);
        Long third = finishFullRoutine(token);

        assertThat(workoutService.getResult(token, first).teamXp().earnedXp()).isEqualTo(65);
        assertThat(workoutService.getResult(token, second).teamXp().earnedXp()).isEqualTo(35);
        assertThat(workoutService.getResult(token, third).teamXp().earnedXp()).isZero();
        assertThat(teamTotalXp()).isEqualTo(before + 100);
    }

    @Test
    @DisplayName("하루 최대치는 참가자별이라 친구는 따로 적립된다")
    void dailyLimitPerParticipant() {
        int before = teamTotalXp();
        String me = participant("아현", true);
        String friend = participant("친구", true);
        finishFullRoutine(me);
        finishFullRoutine(me);

        Long friendWorkout = finishFullRoutine(friend);

        assertThat(workoutService.getResult(friend, friendWorkout).teamXp().earnedXp()).isEqualTo(65);
        assertThat(teamTotalXp()).isEqualTo(before + 165);
    }

    @Test
    @DisplayName("이번 운동으로 팀 레벨 구간을 넘으면 레벨 상승으로 표시된다")
    void leveledUp() {
        int before = teamTotalXp();
        String me = participant("아현", true);
        String friend = participant("친구", true);

        Long myWorkout = finishFullRoutine(me);
        Long friendWorkout = finishFullRoutine(friend);

        boolean myLevelUp = TeamLevel.of(before + 65).level() > TeamLevel.of(before).level();
        boolean friendLevelUp = TeamLevel.of(before + 130).level() > TeamLevel.of(before + 65).level();
        assertThat(workoutService.getResult(me, myWorkout).teamXp().leveledUp()).isEqualTo(myLevelUp);
        assertThat(workoutService.getResult(friend, friendWorkout).teamXp().leveledUp()).isEqualTo(friendLevelUp);
        assertThat(workoutService.getResult(friend, friendWorkout).teamXp().level())
                .isEqualTo(TeamLevel.of(before + 130).level());
    }
}
