package com.team20.fitmate.domain.workout.entity;

import com.team20.fitmate.domain.exercise.entity.Exercise;
import com.team20.fitmate.domain.exercise.entity.MeasureType;
import com.team20.fitmate.domain.routine.entity.RoutineExercise;
import com.team20.fitmate.global.common.BaseTimeEntity;
import com.team20.fitmate.global.exception.CustomException;
import com.team20.fitmate.global.exception.ErrorCode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 수행 기록. 운동 기록 안의 운동 하나에 대한 결과다.
 * <p>
 * 측정하지 못한 값은 0으로 채우지 않고 null로 두며, {@link MeasurementStatus}로 이유를 구분한다.
 * 자세 판정 결과는 형식이 미정이라 AI가 보낸 JSON을 그대로 문자열로 저장한다.
 * 형식이 정해지면 저장 구조는 그대로 두고 읽는 쪽에서 해석한다.
 */
@Getter
@Entity
@Table(name = "exercise_record", uniqueConstraints = @UniqueConstraint(
        name = "uk_exercise_record_workout_routine_exercise",
        columnNames = {"workout_id", "routine_exercise_id"}))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ExerciseRecord extends BaseTimeEntity {

    /** 자세 판정 결과 JSON의 최대 길이 */
    public static final int POSTURE_RESULT_MAX_LENGTH = 4000;

    /** 식별값 */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 소속 운동 기록 */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "workout_id", nullable = false)
    private Workout workout;

    /** 목표로 삼은 루틴 구성 운동. 루틴 없이 운동한 경우(공개 파티) null */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "routine_exercise_id")
    private RoutineExercise routineExercise;

    /** 수행한 운동 */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "exercise_id", nullable = false)
    private Exercise exercise;

    /** 측정 상태 */
    @Enumerated(EnumType.STRING)
    @Column(name = "measurement_status", nullable = false, length = 20)
    private MeasurementStatus measurementStatus;

    /** 유효 횟수. 기준을 충족해 인정된 동작 수. 미측정이거나 시간형이면 null */
    @Column(name = "valid_count")
    private Integer validCount;

    /** 기준 미달 횟수. 자세 피드백이 뜬 동작 수. 미측정이면 null */
    @Column(name = "below_standard_count")
    private Integer belowStandardCount;

    /** 판정 불가 횟수. 인식은 됐지만 판정 근거가 부족한 동작 수. 미측정이면 null */
    @Column(name = "undetermined_count")
    private Integer undeterminedCount;

    /** 실제 소요 시간(초). 시간형은 유효하게 유지한 시간이다. 모르면 null */
    @Column(name = "actual_seconds")
    private Integer actualSeconds;

    /** 수행 세트 수. 모르면 null */
    @Column(name = "performed_sets")
    private Integer performedSets;

    /** 자세 판정 결과(JSON 문자열). 형식 미정. 미측정이면 null */
    @Column(name = "posture_result", length = POSTURE_RESULT_MAX_LENGTH)
    private String postureResult;

    private ExerciseRecord(Workout workout, RoutineExercise routineExercise, Exercise exercise,
                           MeasurementStatus measurementStatus, Integer validCount, Integer belowStandardCount,
                           Integer undeterminedCount, Integer actualSeconds, Integer performedSets,
                           String postureResult) {
        this.workout = workout;
        this.routineExercise = routineExercise;
        this.exercise = exercise;
        this.measurementStatus = measurementStatus;
        this.validCount = validCount;
        this.belowStandardCount = belowStandardCount;
        this.undeterminedCount = undeterminedCount;
        this.actualSeconds = actualSeconds;
        this.performedSets = performedSets;
        this.postureResult = postureResult;
    }

    /**
     * 루틴 구성 운동에 대한 수행 기록을 만든다. 측정 상태와 운동의 측정 방식에 맞지 않는 값이면 거부한다.
     *
     * @throws CustomException 값이 규칙에 맞지 않으면 {@link ErrorCode#INVALID_EXERCISE_RECORD}
     */
    public static ExerciseRecord of(Workout workout, RoutineExercise routineExercise, Values values) {
        Exercise exercise = routineExercise.getExercise();
        validate(exercise.getMeasureType(), values);
        return new ExerciseRecord(workout, routineExercise, exercise, values.measurementStatus(),
                values.validCount(), values.belowStandardCount(), values.undeterminedCount(),
                values.actualSeconds(), values.performedSets(), values.postureResult());
    }

    /**
     * 측정 상태별 규칙을 검사한다.
     * <ul>
     *     <li>음수는 받지 않는다.</li>
     *     <li>측정됨: 횟수형은 유효 횟수, 시간형은 실제 소요 시간이 있어야 한다. 시간형은 유효 횟수를 받지 않는다.</li>
     *     <li>측정 못 함: 유효 횟수·판정 횟수·자세 결과를 받지 않는다(근거 없는 값 금지).</li>
     * </ul>
     */
    private static void validate(MeasureType measureType, Values values) {
        if (isNegative(values.validCount()) || isNegative(values.belowStandardCount())
                || isNegative(values.undeterminedCount()) || isNegative(values.actualSeconds())
                || isNegative(values.performedSets())) {
            throw new CustomException(ErrorCode.INVALID_EXERCISE_RECORD);
        }
        if (values.postureResult() != null && values.postureResult().length() > POSTURE_RESULT_MAX_LENGTH) {
            throw new CustomException(ErrorCode.INVALID_EXERCISE_RECORD);
        }

        if (values.measurementStatus() == MeasurementStatus.NOT_MEASURED) {
            if (values.validCount() != null || values.belowStandardCount() != null
                    || values.undeterminedCount() != null || values.postureResult() != null) {
                throw new CustomException(ErrorCode.INVALID_EXERCISE_RECORD);
            }
            return;
        }

        boolean missingMeasuredValue = measureType == MeasureType.COUNT
                ? values.validCount() == null
                : values.actualSeconds() == null || values.validCount() != null;
        if (missingMeasuredValue) {
            throw new CustomException(ErrorCode.INVALID_EXERCISE_RECORD);
        }
    }

    private static boolean isNegative(Integer value) {
        return value != null && value < 0;
    }

    /**
     * 수행 기록에 담을 값 묶음. 제출 DTO에서 만든다.
     *
     * @param measurementStatus  측정 상태
     * @param validCount         유효 횟수
     * @param belowStandardCount 기준 미달 횟수
     * @param undeterminedCount  판정 불가 횟수
     * @param actualSeconds      실제 소요 시간(초)
     * @param performedSets      수행 세트 수
     * @param postureResult      자세 판정 결과 JSON 문자열
     */
    public record Values(MeasurementStatus measurementStatus, Integer validCount, Integer belowStandardCount,
                         Integer undeterminedCount, Integer actualSeconds, Integer performedSets,
                         String postureResult) {
    }
}
