package com.team20.fitmate.domain.routine.service;

import com.team20.fitmate.domain.participant.dto.ParticipantCreateRequest;
import com.team20.fitmate.domain.participant.service.ParticipantService;
import com.team20.fitmate.domain.routine.dto.RoutineExerciseResponse;
import com.team20.fitmate.domain.routine.dto.RoutineResponse;
import com.team20.fitmate.domain.routine.entity.AdjustmentType;
import com.team20.fitmate.domain.routine.entity.Goal;
import com.team20.fitmate.domain.routine.entity.RoutineStatus;
import com.team20.fitmate.domain.workout.dto.ExerciseRecordRequest;
import com.team20.fitmate.domain.workout.dto.WorkoutFinishRequest;
import com.team20.fitmate.domain.workout.entity.MeasurementStatus;
import com.team20.fitmate.domain.workout.service.WorkoutService;
import com.team20.fitmate.global.exception.CustomException;
import com.team20.fitmate.global.exception.ErrorCode;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 종료된 운동 기록으로 다음 회차 루틴을 만들고, 멱등·본인 확인·종료 여부를 지키는지 확인하는 테스트.
 * 1회차는 자세 개선 루틴(스쿼트 12회 × 3, 힙 힌지 10회 × 3, 플랭크 30초 × 3)이다.
 */
@SpringBootTest
@Transactional
class RoutineNextRoundTest {

    @Autowired
    private RoutineService routineService;

    @Autowired
    private WorkoutService workoutService;

    @Autowired
    private ParticipantService participantService;

    @Autowired
    private EntityManager em;

    /** 참가자를 만들고 토큰을 돌려준다 */
    private String participant(String nickname) {
        return participantService.create(new ParticipantCreateRequest(nickname, "CHARACTER_01")).clientToken();
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

    /** 운동별 목표 수행량(횟수형은 횟수, 시간형은 초) */
    private static List<Integer> amounts(RoutineResponse routine) {
        return routine.exercises().stream()
                .map(e -> e.targetCount() != null ? e.targetCount() : e.targetSeconds())
                .toList();
    }

    /** 수행 기록 오류 코드 확인 */
    private static void assertError(Runnable call, ErrorCode errorCode) {
        assertThatThrownBy(call::run)
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getErrorCode())
                .isEqualTo(errorCode);
    }

    @Test
    @DisplayName("목표를 모두 채우면 상향된 2회차 루틴과 근거 기록·이전 루틴·운동별 사유가 나온다")
    void up() {
        String token = participant("아현");
        RoutineResponse first = routineService.createFirstRound(token, Goal.POSTURE);
        Long workoutId = finish(token, first, 36, 30, 90);

        RoutineResponse next = routineService.createNextRound(token, workoutId);

        assertThat(next.roundNo()).isEqualTo(2);
        assertThat(next.goal()).isEqualTo(Goal.POSTURE);
        assertThat(next.status()).isEqualTo(RoutineStatus.COMPLETED);
        assertThat(next.adjustmentType()).isEqualTo(AdjustmentType.UP);
        assertThat(next.previousRoutineId()).isEqualTo(first.routineId());
        assertThat(next.basedOnWorkoutId()).isEqualTo(workoutId);
        assertThat(amounts(next)).containsExactly(14, 12, 40);
        assertThat(next.adjustmentReasons()).hasSize(3)
                .first().isEqualTo("스쿼트: 목표의 100%를 수행해 12회 → 14회로 늘렸어요.");
        // 운동 구성·순서·세트·태그는 그대로다
        assertThat(next.exercises()).extracting(RoutineExerciseResponse::exerciseCode)
                .containsExactlyElementsOf(first.exercises().stream().map(RoutineExerciseResponse::exerciseCode).toList());
        assertThat(next.exercises()).extracting(RoutineExerciseResponse::targetSets)
                .containsExactlyElementsOf(first.exercises().stream().map(RoutineExerciseResponse::targetSets).toList());
        assertThat(next.exercises()).extracting(RoutineExerciseResponse::tag)
                .containsExactlyElementsOf(first.exercises().stream().map(RoutineExerciseResponse::tag).toList());
    }

    @Test
    @DisplayName("수행이 부족하면 수행량을 올리지 않고 유지 사유를 보여준다")
    void keep() {
        String token = participant("아현");
        RoutineResponse first = routineService.createFirstRound(token, Goal.POSTURE);
        Long workoutId = finish(token, first, 18, 15, 40);

        RoutineResponse next = routineService.createNextRound(token, workoutId);

        assertThat(next.adjustmentType()).isEqualTo(AdjustmentType.KEEP);
        assertThat(amounts(next)).containsExactlyElementsOf(amounts(first));
        assertThat(next.adjustmentReasons()).contains("스쿼트: 목표의 50%를 수행해 같은 난도를 유지해요.");
    }

    @Test
    @DisplayName("모든 운동을 측정하지 못했으면 분석 불가로 안내하고 수행량을 올리지 않는다")
    void unavailable() {
        String token = participant("아현");
        RoutineResponse first = routineService.createFirstRound(token, Goal.POSTURE);
        Long workoutId = finish(token, first, null, null, null);

        RoutineResponse next = routineService.createNextRound(token, workoutId);

        assertThat(next.adjustmentType()).isEqualTo(AdjustmentType.UNAVAILABLE);
        assertThat(amounts(next)).containsExactlyElementsOf(amounts(first));
        assertThat(next.adjustmentReasons()).allMatch(reason -> reason.endsWith("기록이 부족해 같은 난도를 유지해요."));
    }

    @Test
    @DisplayName("같은 운동 기록으로 다시 요청하면 새로 만들지 않고 처음 추천을 돌려준다")
    void idempotent() {
        String token = participant("아현");
        RoutineResponse first = routineService.createFirstRound(token, Goal.POSTURE);
        Long workoutId = finish(token, first, 36, 30, 90);

        RoutineResponse once = routineService.createNextRound(token, workoutId);
        em.flush();
        em.clear();
        RoutineResponse again = routineService.createNextRound(token, workoutId);

        assertThat(again).isEqualTo(once);
    }

    @Test
    @DisplayName("다른 참가자의 운동 기록으로는 추천받을 수 없고, 추천 루틴도 본인만 조회한다")
    void ownerOnly() {
        String owner = participant("아현");
        String other = participant("다른사람");
        RoutineResponse first = routineService.createFirstRound(owner, Goal.POSTURE);
        Long workoutId = finish(owner, first, 36, 30, 90);
        RoutineResponse next = routineService.createNextRound(owner, workoutId);

        assertError(() -> routineService.createNextRound(other, workoutId), ErrorCode.WORKOUT_NOT_FOUND);
        assertError(() -> routineService.get(other, next.routineId()), ErrorCode.ROUTINE_NOT_FOUND);
        assertThat(routineService.get(owner, next.routineId())).isEqualTo(next);
    }

    @Test
    @DisplayName("아직 종료하지 않은 운동 기록이면 409-3으로 거부한다")
    void notFinished() {
        String token = participant("아현");
        RoutineResponse first = routineService.createFirstRound(token, Goal.POSTURE);
        Long workoutId = workoutService.start(token, first.routineId()).workoutId();

        assertError(() -> routineService.createNextRound(token, workoutId), ErrorCode.WORKOUT_NOT_FINISHED);
    }

    @Test
    @DisplayName("2회차 루틴으로 운동을 시작할 수 있고, 그 기록으로 3회차를 추천받는다")
    void secondRoundPlayable() {
        String token = participant("아현");
        RoutineResponse first = routineService.createFirstRound(token, Goal.POSTURE);
        RoutineResponse second = routineService.createNextRound(token, finish(token, first, 36, 30, 90));

        Long secondWorkoutId = finish(token, second, 42, 36, 120);
        RoutineResponse third = routineService.createNextRound(token, secondWorkoutId);

        assertThat(third.roundNo()).isEqualTo(3);
        assertThat(third.previousRoutineId()).isEqualTo(second.routineId());
        assertThat(amounts(third)).containsExactly(17, 14, 50);
    }

    @Test
    @DisplayName("첫 회차 루틴은 근거 기록·조정 유형이 비어 있다")
    void firstRoundHasNoAdjustment() {
        RoutineResponse first = routineService.createFirstRound(participant("아현"), Goal.POSTURE);

        assertThat(first.previousRoutineId()).isNull();
        assertThat(first.basedOnWorkoutId()).isNull();
        assertThat(first.adjustmentType()).isNull();
        assertThat(first.adjustmentReasons()).isEmpty();
    }
}
