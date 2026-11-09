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
import com.team20.fitmate.domain.workout.entity.ExerciseRecord;
import com.team20.fitmate.domain.workout.entity.MeasurementStatus;
import com.team20.fitmate.domain.workout.entity.Workout;
import com.team20.fitmate.domain.workout.repository.ExerciseRecordRepository;
import com.team20.fitmate.domain.workout.repository.WorkoutRepository;
import com.team20.fitmate.domain.workout.service.WorkoutService;
import com.team20.fitmate.domain.xp.calculator.XpCalculator;
import com.team20.fitmate.domain.xp.entity.TeamXpLog;
import com.team20.fitmate.domain.xp.entity.XpStatus;
import com.team20.fitmate.domain.xp.repository.TeamXpLogRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 팀 경험치 적립과 중복 적립 방지를 확인하는 테스트.
 * <p>
 * 실제 산식은 PM이 정하므로, 이 테스트에서만 "유효 횟수 합"을 경험치로 쓰는 테스트용 산식을 넣는다.
 */
@SpringBootTest
@Transactional
class TeamXpServiceTest {

    /** 테스트 전용 산식: 유효 횟수의 합. 운영 코드에는 쓰지 않는다 */
    @TestConfiguration
    static class TestXpCalculatorConfig {

        @Bean
        @Primary
        XpCalculator testXpCalculator() {
            return new XpCalculator() {
                @Override
                public boolean isDecided() {
                    return true;
                }

                @Override
                public int calculate(List<ExerciseRecord> records) {
                    return records.stream().map(ExerciseRecord::getValidCount).filter(Objects::nonNull)
                            .mapToInt(Integer::intValue).sum();
                }
            };
        }
    }

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

    /** 자세 개선 루틴으로 운동을 시작해 스쿼트 유효 횟수만 보내고 종료한 뒤 운동 기록 식별값을 돌려준다 */
    private Long finishWorkout(String token, int squatValidCount) {
        RoutineResponse routine = routineService.createFirstRound(token, Goal.POSTURE);
        Long workoutId = workoutService.start(token, routine.routineId()).workoutId();
        ExerciseRecordRequest squat = new ExerciseRecordRequest(routine.exercises().getFirst().routineExerciseId(),
                MeasurementStatus.MEASURED, squatValidCount, 0, 0, 60, 3, null);
        workoutService.finish(token, workoutId, new WorkoutFinishRequest(List.of(squat)));
        return workoutId;
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
        assertThat(result.teamXp().level()).isNull();
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
}
