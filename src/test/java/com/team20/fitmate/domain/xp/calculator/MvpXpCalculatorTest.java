package com.team20.fitmate.domain.xp.calculator;

import com.team20.fitmate.domain.exercise.entity.Exercise;
import com.team20.fitmate.domain.exercise.entity.MeasureType;
import com.team20.fitmate.domain.routine.entity.RoutineExercise;
import com.team20.fitmate.domain.workout.entity.ExerciseRecord;
import com.team20.fitmate.domain.workout.entity.MeasurementStatus;
import com.team20.fitmate.domain.workout.entity.Workout;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link MvpXpCalculator}의 PM 확정 산식을 확인하는 단위 테스트.
 * 루틴은 스쿼트 10회 × 3세트(목표 30회), 플랭크 20초 × 3세트다.
 */
class MvpXpCalculatorTest {

    private final MvpXpCalculator calculator = new MvpXpCalculator();

    private final Workout workout = Workout.start(null, null, null, null);

    private final RoutineExercise squat = RoutineExercise.of(
            Exercise.create("SQUAT", "스쿼트", MeasureType.COUNT, true), 1, 10, 3);

    private final RoutineExercise plank = RoutineExercise.of(
            Exercise.create("PLANK", "플랭크", MeasureType.TIME, true), 2, 20, 3);

    private ExerciseRecord squatRecord(int validCount) {
        return ExerciseRecord.of(workout, squat,
                new ExerciseRecord.Values(MeasurementStatus.MEASURED, validCount, 0, 0, 60, 3, null));
    }

    private ExerciseRecord plankRecord(int actualSeconds) {
        return ExerciseRecord.of(workout, plank,
                new ExerciseRecord.Values(MeasurementStatus.MEASURED, null, null, null, actualSeconds, 3, null));
    }

    private ExerciseRecord notMeasured(RoutineExercise routineExercise) {
        return ExerciseRecord.of(workout, routineExercise, ExerciseRecord.Values.notMeasured());
    }

    @Test
    @DisplayName("횟수형은 유효 횟수 1회당 1XP")
    void countXp() {
        assertThat(calculator.calculate(List.of(squatRecord(20), notMeasured(plank)))).isEqualTo(20);
    }

    @Test
    @DisplayName("횟수형은 목표 횟수(세트당 횟수 × 세트 수)까지만 준다")
    void countXpCappedAtTarget() {
        assertThat(calculator.calculate(List.of(squatRecord(45), notMeasured(plank)))).isEqualTo(30);
    }

    @Test
    @DisplayName("시간형은 목표 시간을 채운 세트 1개당 5XP, 추천 세트 수까지만 준다")
    void timeXp() {
        assertThat(calculator.calculate(List.of(notMeasured(squat), plankRecord(45)))).isEqualTo(10);
        assertThat(calculator.calculate(List.of(notMeasured(squat), plankRecord(19)))).isZero();
        assertThat(calculator.calculate(List.of(notMeasured(squat), plankRecord(200)))).isEqualTo(15);
    }

    @Test
    @DisplayName("모든 운동이 목표를 채우면 20XP 보너스를 더한다")
    void routineCompleteBonus() {
        assertThat(calculator.calculate(List.of(squatRecord(30), plankRecord(60)))).isEqualTo(30 + 15 + 20);
    }

    @Test
    @DisplayName("하나라도 목표에 못 미치거나 측정 못 했으면 보너스가 없다")
    void noBonusWhenAnyMissed() {
        assertThat(calculator.calculate(List.of(squatRecord(29), plankRecord(60)))).isEqualTo(29 + 15);
        assertThat(calculator.calculate(List.of(squatRecord(30), notMeasured(plank)))).isEqualTo(30);
    }

    @Test
    @DisplayName("수행 기록이 없으면 0XP")
    void empty() {
        assertThat(calculator.calculate(List.of())).isZero();
    }
}
