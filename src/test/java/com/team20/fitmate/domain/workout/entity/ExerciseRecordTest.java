package com.team20.fitmate.domain.workout.entity;

import com.team20.fitmate.domain.exercise.entity.Exercise;
import com.team20.fitmate.domain.exercise.entity.MeasureType;
import com.team20.fitmate.domain.routine.entity.RoutineExercise;
import com.team20.fitmate.global.exception.CustomException;
import com.team20.fitmate.global.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static com.team20.fitmate.domain.workout.entity.MeasurementStatus.MEASURED;
import static com.team20.fitmate.domain.workout.entity.MeasurementStatus.NOT_MEASURED;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link ExerciseRecord}가 측정 상태와 측정 방식에 맞는 값만 받는지 확인하는 단위 테스트.
 */
class ExerciseRecordTest {

    private final RoutineExercise squat = RoutineExercise.of(
            Exercise.create("SQUAT", "스쿼트", MeasureType.COUNT, true), 1, 10, 3);
    private final RoutineExercise plank = RoutineExercise.of(
            Exercise.create("PLANK", "플랭크", MeasureType.TIME, true), 2, 20, 3);
    private final Workout workout = Workout.start(null, null, null, null);

    /** 값 묶음을 짧게 만든다 */
    private static ExerciseRecord.Values values(MeasurementStatus status, Integer valid, Integer below,
                                                Integer undetermined, Integer seconds, String posture) {
        return new ExerciseRecord.Values(status, valid, below, undetermined, seconds, 3, posture);
    }

    /** 수행 기록 생성이 INVALID_EXERCISE_RECORD로 거부되는지 확인한다 */
    private static void assertRejected(Runnable creation) {
        assertThatThrownBy(creation::run)
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_EXERCISE_RECORD);
    }

    @Test
    @DisplayName("횟수형을 측정하면 유효·기준 미달·판정 불가 횟수와 자세 결과를 그대로 담는다")
    void countMeasured() {
        ExerciseRecord record = ExerciseRecord.of(workout, squat,
                values(MEASURED, 10, 2, 1, 95, "{\"feedback\":[\"무릎\"]}"));

        assertThat(record.getExercise().getCode()).isEqualTo("SQUAT");
        assertThat(record.getValidCount()).isEqualTo(10);
        assertThat(record.getBelowStandardCount()).isEqualTo(2);
        assertThat(record.getUndeterminedCount()).isEqualTo(1);
        assertThat(record.getPostureResult()).isEqualTo("{\"feedback\":[\"무릎\"]}");
    }

    @Test
    @DisplayName("0회도 측정된 값으로 받는다")
    void zeroIsMeasured() {
        ExerciseRecord record = ExerciseRecord.of(workout, squat, values(MEASURED, 0, 5, 0, 60, null));

        assertThat(record.getValidCount()).isZero();
    }

    @Test
    @DisplayName("횟수형을 측정했는데 유효 횟수가 없으면 거부한다")
    void countMeasuredWithoutValidCount() {
        assertRejected(() -> ExerciseRecord.of(workout, squat, values(MEASURED, null, 2, 1, 95, null)));
    }

    @Test
    @DisplayName("시간형은 실제 소요 시간으로 측정하고 유효 횟수는 비운다")
    void timeMeasured() {
        ExerciseRecord record = ExerciseRecord.of(workout, plank, values(MEASURED, null, null, null, 45, null));

        assertThat(record.getActualSeconds()).isEqualTo(45);
        assertThat(record.getValidCount()).isNull();
    }

    @Test
    @DisplayName("시간형을 측정했는데 소요 시간이 없거나 유효 횟수가 있으면 거부한다")
    void timeMeasuredInvalid() {
        assertRejected(() -> ExerciseRecord.of(workout, plank, values(MEASURED, null, null, null, null, null)));
        assertRejected(() -> ExerciseRecord.of(workout, plank, values(MEASURED, 30, null, null, 45, null)));
    }

    @Test
    @DisplayName("측정 못 함은 지나간 시간만 받고 횟수·자세 결과는 null로 둔다")
    void notMeasured() {
        ExerciseRecord record = ExerciseRecord.of(workout, squat, values(NOT_MEASURED, null, null, null, 40, null));

        assertThat(record.getMeasurementStatus()).isEqualTo(NOT_MEASURED);
        assertThat(record.getValidCount()).isNull();
        assertThat(record.getPostureResult()).isNull();
        assertThat(record.getActualSeconds()).isEqualTo(40);
    }

    @Test
    @DisplayName("측정 못 함인데 횟수나 자세 결과가 오면 근거 없는 값이라 거부한다")
    void notMeasuredWithValues() {
        assertRejected(() -> ExerciseRecord.of(workout, squat, values(NOT_MEASURED, 0, null, null, null, null)));
        assertRejected(() -> ExerciseRecord.of(workout, squat, values(NOT_MEASURED, null, 1, null, null, null)));
        assertRejected(() -> ExerciseRecord.of(workout, squat, values(NOT_MEASURED, null, null, null, null, "{}")));
    }

    @Test
    @DisplayName("음수 값은 거부한다")
    void negative() {
        assertRejected(() -> ExerciseRecord.of(workout, squat, values(MEASURED, -1, 0, 0, 10, null)));
        assertRejected(() -> ExerciseRecord.of(workout, squat, values(MEASURED, 10, 0, 0, -5, null)));
    }

    @Test
    @DisplayName("자세 결과 JSON이 최대 길이를 넘으면 거부한다")
    void postureTooLong() {
        String tooLong = "\"" + "a".repeat(ExerciseRecord.POSTURE_RESULT_MAX_LENGTH) + "\"";

        assertRejected(() -> ExerciseRecord.of(workout, squat, values(MEASURED, 10, 0, 0, 10, tooLong)));
    }
}
