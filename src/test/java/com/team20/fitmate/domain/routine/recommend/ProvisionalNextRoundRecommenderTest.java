package com.team20.fitmate.domain.routine.recommend;

import com.team20.fitmate.domain.exercise.entity.Exercise;
import com.team20.fitmate.domain.exercise.entity.MeasureType;
import com.team20.fitmate.domain.routine.entity.AdjustmentType;
import com.team20.fitmate.domain.routine.entity.RoutineExercise;
import com.team20.fitmate.domain.workout.entity.ExerciseRecord;
import com.team20.fitmate.domain.workout.entity.MeasurementStatus;
import com.team20.fitmate.domain.workout.entity.Workout;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 시연용 임시 다음 회차 규칙이 상향·유지·분석 불가와 안전 상한을 프론트 임시 규칙과 같게 정하는지 확인하는 단위 테스트.
 */
class ProvisionalNextRoundRecommenderTest {

    private final NextRoundRecommender recommender = new ProvisionalNextRoundRecommender();

    private final Workout workout = Workout.start(null, null, null, null);

    /** 횟수형 운동 (목표 횟수 × 세트) */
    private static RoutineExercise count(String name, int targetCount, int sets) {
        return RoutineExercise.of(Exercise.create("C_" + name, name, MeasureType.COUNT, true), 1, targetCount, sets);
    }

    /** 시간형 운동 (목표 초 × 세트) */
    private static RoutineExercise time(String name, int targetSeconds, int sets) {
        return RoutineExercise.of(Exercise.create("T_" + name, name, MeasureType.TIME, true), 1, targetSeconds, sets);
    }

    /** 횟수형 측정 결과 */
    private NextRoundRecommender.ExerciseResult counted(RoutineExercise target, int validCount) {
        return new NextRoundRecommender.ExerciseResult(target, ExerciseRecord.of(workout, target,
                new ExerciseRecord.Values(MeasurementStatus.MEASURED, validCount, 0, 0, 60, 3, null)));
    }

    /** 시간형 측정 결과 */
    private NextRoundRecommender.ExerciseResult timed(RoutineExercise target, int seconds) {
        return new NextRoundRecommender.ExerciseResult(target, ExerciseRecord.of(workout, target,
                new ExerciseRecord.Values(MeasurementStatus.MEASURED, null, null, null, seconds, 3, null)));
    }

    /** 측정 못 한 결과 */
    private NextRoundRecommender.ExerciseResult notMeasured(RoutineExercise target) {
        return new NextRoundRecommender.ExerciseResult(target,
                ExerciseRecord.of(workout, target, ExerciseRecord.Values.notMeasured()));
    }

    /** 다음 회차 수행량 목록 */
    private static List<Integer> amounts(NextRoundPlan plan) {
        return plan.exercises().stream().map(NextRoundPlan.AdjustedExercise::amount).toList();
    }

    /** 조정 사유 목록 */
    private static List<String> reasons(NextRoundPlan plan) {
        return plan.exercises().stream().map(NextRoundPlan.AdjustedExercise::reason).toList();
    }

    @Test
    @DisplayName("목표를 충분히 채우면 횟수형은 15% 올리고 시간형은 10초 늘려 상향한다")
    void up() {
        NextRoundPlan plan = recommender.plan(List.of(
                counted(count("스쿼트", 12, 3), 36),
                counted(count("힙 힌지", 10, 3), 30),
                timed(time("플랭크", 30, 3), 90)));

        assertThat(plan.adjustmentType()).isEqualTo(AdjustmentType.UP);
        assertThat(amounts(plan)).containsExactly(14, 12, 40);
        assertThat(reasons(plan)).containsExactly(
                "스쿼트: 목표의 100%를 수행해 12회 → 14회로 늘렸어요.",
                "힙 힌지: 목표의 100%를 수행해 10회 → 12회로 늘렸어요.",
                "플랭크: 목표의 100%를 수행해 30초 → 40초로 늘렸어요.");
    }

    @Test
    @DisplayName("목표의 90% 미만이면 일괄 상향하지 않고 유지한다")
    void keepWhenInsufficient() {
        NextRoundPlan plan = recommender.plan(List.of(
                counted(count("스쿼트", 12, 3), 20),
                timed(time("플랭크", 30, 3), 45)));

        assertThat(plan.adjustmentType()).isEqualTo(AdjustmentType.KEEP);
        assertThat(amounts(plan)).containsExactly(12, 30);
        assertThat(reasons(plan)).containsExactly(
                "스쿼트: 목표의 56%를 수행해 같은 난도를 유지해요.",
                "플랭크: 목표의 50%를 수행해 같은 난도를 유지해요.");
    }

    @Test
    @DisplayName("판정은 반올림 전 비율로 한다: 89.6%는 유지, 정확히 90%는 상향")
    void boundary() {
        NextRoundPlan below = recommender.plan(List.of(counted(count("스쿼트", 16, 3), 43)));
        NextRoundPlan exact = recommender.plan(List.of(counted(count("스쿼트", 10, 3), 27)));

        assertThat(below.adjustmentType()).isEqualTo(AdjustmentType.KEEP);
        assertThat(reasons(below)).containsExactly("스쿼트: 목표의 90%를 수행해 같은 난도를 유지해요.");
        assertThat(exact.adjustmentType()).isEqualTo(AdjustmentType.UP);
        assertThat(amounts(exact)).containsExactly(12);
    }

    @Test
    @DisplayName("모든 운동을 측정하지 못했거나 기록이 없으면 분석 불가이고 수행량을 올리지 않는다")
    void unavailable() {
        RoutineExercise squat = count("스쿼트", 12, 3);
        NextRoundPlan plan = recommender.plan(List.of(
                notMeasured(squat),
                new NextRoundRecommender.ExerciseResult(time("플랭크", 30, 3), null)));

        assertThat(plan.adjustmentType()).isEqualTo(AdjustmentType.UNAVAILABLE);
        assertThat(amounts(plan)).containsExactly(12, 30);
        assertThat(reasons(plan)).containsExactly(
                "스쿼트: 기록이 부족해 같은 난도를 유지해요.",
                "플랭크: 기록이 부족해 같은 난도를 유지해요.");
    }

    @Test
    @DisplayName("일부만 측정됐으면 측정된 운동만 평가하고, 측정 못 한 운동은 유지한다")
    void partial() {
        NextRoundPlan plan = recommender.plan(List.of(
                counted(count("스쿼트", 12, 3), 36),
                notMeasured(count("런지", 10, 2))));

        assertThat(plan.adjustmentType()).isEqualTo(AdjustmentType.UP);
        assertThat(amounts(plan)).containsExactly(14, 10);
    }

    @Test
    @DisplayName("안전 상한(30회, 60초)을 넘지 않고, 이미 상한이면 유지한다")
    void safetyCap() {
        NextRoundPlan nearCap = recommender.plan(List.of(
                counted(count("스쿼트", 28, 3), 84),
                timed(time("플랭크", 55, 3), 165)));
        NextRoundPlan atCap = recommender.plan(List.of(
                counted(count("스쿼트", 30, 3), 90),
                timed(time("플랭크", 60, 3), 180)));

        assertThat(nearCap.adjustmentType()).isEqualTo(AdjustmentType.UP);
        assertThat(amounts(nearCap)).containsExactly(30, 60);
        assertThat(atCap.adjustmentType()).isEqualTo(AdjustmentType.KEEP);
        assertThat(amounts(atCap)).containsExactly(30, 60);
        assertThat(reasons(atCap)).containsExactly(
                "스쿼트: 안전 상한에 도달해 같은 난도를 유지해요.",
                "플랭크: 안전 상한에 도달해 같은 난도를 유지해요.");
    }

    @Test
    @DisplayName("같은 입력에는 같은 결과를 낸다")
    void deterministic() {
        RoutineExercise squat = count("스쿼트", 12, 3);
        List<NextRoundRecommender.ExerciseResult> input = List.of(counted(squat, 33));

        assertThat(recommender.plan(input)).isEqualTo(recommender.plan(input));
    }
}
