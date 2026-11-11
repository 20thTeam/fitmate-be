package com.team20.fitmate.domain.history.service;

import com.team20.fitmate.domain.history.dto.HistoryResponse;
import com.team20.fitmate.domain.history.dto.HistoryWorkoutResponse;
import com.team20.fitmate.domain.participant.dto.ParticipantCreateRequest;
import com.team20.fitmate.domain.participant.service.ParticipantService;
import com.team20.fitmate.domain.room.service.RoomService;
import com.team20.fitmate.domain.routine.dto.RoutineResponse;
import com.team20.fitmate.domain.routine.entity.AdjustmentType;
import com.team20.fitmate.domain.routine.entity.Goal;
import com.team20.fitmate.domain.routine.service.RoutineService;
import com.team20.fitmate.domain.workout.dto.ExerciseRecordRequest;
import com.team20.fitmate.domain.workout.dto.ExerciseRecordResponse;
import com.team20.fitmate.domain.workout.dto.WorkoutFinishRequest;
import com.team20.fitmate.domain.workout.entity.MeasurementStatus;
import com.team20.fitmate.domain.workout.service.WorkoutService;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 성장 기록이 저장된 실제 기록과 맞고, 기록이 없으면 빈 상태를, 다른 참가자 기록은 보여주지 않는지 확인하는 테스트.
 * 1회차는 자세 개선 루틴(스쿼트 12회 × 3, 힙 힌지 10회 × 3, 플랭크 30초 × 3)이다.
 */
@SpringBootTest
@Transactional
class HistoryServiceTest {

    @Autowired
    private HistoryService historyService;

    @Autowired
    private RoutineService routineService;

    @Autowired
    private WorkoutService workoutService;

    @Autowired
    private ParticipantService participantService;

    @Autowired
    private RoomService roomService;

    @Autowired
    private EntityManager em;

    /** 참가자를 만들고 (선택) 지정 운동방에 입장시킨 뒤 토큰을 돌려준다 */
    private String participant(String nickname, boolean enterRoom) {
        String token = participantService.create(new ParticipantCreateRequest(nickname, "CHARACTER_01")).clientToken();
        if (enterRoom) {
            roomService.enter(token, roomService.getAll().getFirst().roomId());
        }
        return token;
    }

    /** 루틴에서 운동 코드로 구성 운동 식별값을 찾는다 */
    private static Long routineExerciseId(RoutineResponse routine, String exerciseCode) {
        return routine.exercises().stream()
                .filter(exercise -> exercise.exerciseCode().equals(exerciseCode))
                .findFirst().orElseThrow().routineExerciseId();
    }

    /** 루틴으로 운동을 시작해 주어진 값(null이면 보내지 않음 = 측정 못 함)으로 종료하고 운동 기록 식별값을 돌려준다 */
    private Long finish(String token, RoutineResponse routine, Integer squat, Integer hipHinge, Integer plankSeconds) {
        Long workoutId = workoutService.start(token, routine.routineId()).workoutId();
        List<ExerciseRecordRequest> records = new ArrayList<>();
        if (squat != null) {
            records.add(new ExerciseRecordRequest(routineExerciseId(routine, "SQUAT"),
                    MeasurementStatus.MEASURED, squat, 0, 0, 60, 3, null));
        }
        if (hipHinge != null) {
            records.add(new ExerciseRecordRequest(routineExerciseId(routine, "HIP_HINGE_DEADLIFT"),
                    MeasurementStatus.MEASURED, hipHinge, 0, 0, 60, 3, null));
        }
        if (plankSeconds != null) {
            records.add(new ExerciseRecordRequest(routineExerciseId(routine, "PLANK"),
                    MeasurementStatus.MEASURED, null, null, null, plankSeconds, 3, null));
        }
        workoutService.finish(token, workoutId, new WorkoutFinishRequest(records));
        return workoutId;
    }

    @Test
    @DisplayName("기록이 없으면 빈 목록과 0·null 누적량을 돌려주고 예시 수치를 만들지 않는다")
    void empty() {
        HistoryResponse history = historyService.getMyHistory(participant("아현", false));

        assertThat(history.workouts()).isEmpty();
        assertThat(history.summary().workoutCount()).isZero();
        assertThat(history.summary().totalElapsedSeconds()).isZero();
        assertThat(history.summary().totalValidCount()).isNull();
        assertThat(history.summary().totalExerciseSeconds()).isNull();
        assertThat(history.summary().totalEarnedXp()).isZero();
    }

    @Test
    @DisplayName("진행 중인 운동은 확정 전이라 성장 기록에 넣지 않는다")
    void excludeInProgress() {
        String token = participant("아현", false);
        RoutineResponse routine = routineService.createFirstRound(token, Goal.POSTURE);
        workoutService.start(token, routine.routineId());

        assertThat(historyService.getMyHistory(token).workouts()).isEmpty();
    }

    @Test
    @DisplayName("1·2회차를 마치면 최근 순으로 회차별 기록·변경 이유·다음 추천·누적량이 저장값과 같게 나온다")
    void twoRounds() {
        String token = participant("아현", true);
        RoutineResponse first = routineService.createFirstRound(token, Goal.POSTURE);
        Long firstWorkoutId = finish(token, first, 36, 30, 90);
        RoutineResponse second = routineService.createNextRound(token, firstWorkoutId);
        Long secondWorkoutId = finish(token, second, 20, null, null);
        em.flush();
        em.clear();

        HistoryResponse history = historyService.getMyHistory(token);

        assertThat(history.workouts()).extracting(HistoryWorkoutResponse::workoutId)
                .containsExactly(secondWorkoutId, firstWorkoutId);
        HistoryWorkoutResponse round2 = history.workouts().get(0);
        HistoryWorkoutResponse round1 = history.workouts().get(1);

        // 1회차: 목표를 모두 채움(101XP지만 하루 최대 100XP). 다음 추천은 2회차 상향
        assertThat(round1.roundNo()).isEqualTo(1);
        assertThat(round1.routineId()).isEqualTo(first.routineId());
        assertThat(round1.adjustmentType()).isNull();
        assertThat(round1.adjustmentReasons()).isEmpty();
        assertThat(round1.totalValidCount()).isEqualTo(66);
        assertThat(round1.exerciseSeconds()).isEqualTo(210);
        assertThat(round1.earnedXp()).isEqualTo(100);
        assertThat(round1.records()).extracting(ExerciseRecordResponse::exerciseCode)
                .containsExactly("SQUAT", "HIP_HINGE_DEADLIFT", "PLANK");
        assertThat(round1.nextRoutine().routineId()).isEqualTo(second.routineId());
        assertThat(round1.nextRoutine().roundNo()).isEqualTo(2);
        assertThat(round1.nextRoutine().adjustmentType()).isEqualTo(AdjustmentType.UP);
        assertThat(round1.nextRoutine().adjustmentReasons()).hasSize(3);

        // 2회차: 스쿼트만 측정. 이번 회차가 바뀐 이유가 붙고, 아직 다음 추천은 없음. 하루 최대치라 0XP
        assertThat(round2.roundNo()).isEqualTo(2);
        assertThat(round2.adjustmentType()).isEqualTo(AdjustmentType.UP);
        assertThat(round2.adjustmentReasons()).isEqualTo(second.adjustmentReasons());
        assertThat(round2.totalValidCount()).isEqualTo(20);
        assertThat(round2.exerciseSeconds()).isEqualTo(60);
        assertThat(round2.earnedXp()).isZero();
        assertThat(round2.records()).extracting(ExerciseRecordResponse::measurementStatus)
                .containsExactly(MeasurementStatus.MEASURED, MeasurementStatus.NOT_MEASURED,
                        MeasurementStatus.NOT_MEASURED);
        assertThat(round2.nextRoutine()).isNull();

        assertThat(history.summary().workoutCount()).isEqualTo(2);
        assertThat(history.summary().totalValidCount()).isEqualTo(86);
        assertThat(history.summary().totalExerciseSeconds()).isEqualTo(270);
        assertThat(history.summary().totalEarnedXp()).isEqualTo(100);
        assertThat(history.summary().totalElapsedSeconds())
                .isEqualTo(round1.elapsedSeconds() + round2.elapsedSeconds());
    }

    @Test
    @DisplayName("모든 운동을 측정하지 못했으면 유효 횟수 합은 0이 아니라 null이고, 팀이 없으면 경험치도 null이다")
    void notMeasuredIsNull() {
        String token = participant("아현", false);
        RoutineResponse routine = routineService.createFirstRound(token, Goal.POSTURE);
        finish(token, routine, null, null, null);

        HistoryResponse history = historyService.getMyHistory(token);

        HistoryWorkoutResponse item = history.workouts().getFirst();
        assertThat(item.totalValidCount()).isNull();
        assertThat(item.exerciseSeconds()).isNull();
        assertThat(item.earnedXp()).isNull();
        assertThat(history.summary().workoutCount()).isEqualTo(1);
        assertThat(history.summary().totalValidCount()).isNull();
        assertThat(history.summary().totalEarnedXp()).isZero();
    }

    @Test
    @DisplayName("다른 참가자의 기록은 내 성장 기록에 나오지 않는다")
    void ownOnly() {
        String owner = participant("아현", false);
        String other = participant("다른사람", false);
        Long ownerWorkoutId = finish(owner, routineService.createFirstRound(owner, Goal.POSTURE), 36, 30, 90);
        Long otherWorkoutId = finish(other, routineService.createFirstRound(other, Goal.POSTURE), 10, null, null);

        assertThat(historyService.getMyHistory(owner).workouts())
                .extracting(HistoryWorkoutResponse::workoutId).containsExactly(ownerWorkoutId);
        assertThat(historyService.getMyHistory(other).workouts())
                .extracting(HistoryWorkoutResponse::workoutId).containsExactly(otherWorkoutId);
    }
}
