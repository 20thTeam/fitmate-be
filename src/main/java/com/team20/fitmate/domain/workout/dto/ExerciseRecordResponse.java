package com.team20.fitmate.domain.workout.dto;

import com.fasterxml.jackson.annotation.JsonRawValue;
import com.team20.fitmate.domain.exercise.entity.MeasureType;
import com.team20.fitmate.domain.workout.entity.ExerciseRecord;
import com.team20.fitmate.domain.workout.entity.MeasurementStatus;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 운동 하나의 확정된 수행 기록 응답.
 *
 * @param recordId           수행 기록 식별값
 * @param routineExerciseId  루틴 구성 운동 식별값
 * @param exerciseCode       운동 코드
 * @param exerciseName       운동 이름
 * @param measureType        측정 방식
 * @param measurementStatus  측정 상태
 * @param validCount         유효 횟수
 * @param belowStandardCount 기준 미달 횟수
 * @param undeterminedCount  판정 불가 횟수
 * @param actualSeconds      실제 소요 시간(초)
 * @param performedSets      수행 세트 수
 * @param postureResult      자세 판정 결과 (받은 JSON 그대로)
 */
public record ExerciseRecordResponse(

        @Schema(description = "수행 기록 식별값", example = "1")
        Long recordId,

        @Schema(description = "루틴 구성 운동 식별값", example = "1")
        Long routineExerciseId,

        @Schema(description = "운동 코드", example = "SQUAT")
        String exerciseCode,

        @Schema(description = "운동 이름", example = "스쿼트")
        String exerciseName,

        @Schema(description = "측정 방식 (COUNT 횟수, TIME 시간)", example = "COUNT")
        MeasureType measureType,

        @Schema(description = "측정 상태 (MEASURED 측정됨, NOT_MEASURED 측정 못 함)", example = "MEASURED")
        MeasurementStatus measurementStatus,

        @Schema(description = "유효 횟수. 측정 못 함·시간형은 null", example = "10", nullable = true)
        Integer validCount,

        @Schema(description = "기준 미달 횟수. 측정 못 함은 null", example = "2", nullable = true)
        Integer belowStandardCount,

        @Schema(description = "판정 불가 횟수. 측정 못 함은 null", example = "1", nullable = true)
        Integer undeterminedCount,

        @Schema(description = "실제 소요 시간(초)", example = "95", nullable = true)
        Integer actualSeconds,

        @Schema(description = "수행 세트 수", example = "3", nullable = true)
        Integer performedSets,

        @Schema(description = "자세 판정 결과. 제출한 JSON 그대로 돌려준다", type = "object", nullable = true,
                example = "{\"feedback\": [\"무릎이 발끝을 넘어요\"]}")
        @JsonRawValue
        String postureResult
) {

    /**
     * 수행 기록 엔티티를 응답으로 바꾼다. 트랜잭션 안에서 불러야 운동 정보를 읽을 수 있다.
     */
    public static ExerciseRecordResponse from(ExerciseRecord record) {
        return new ExerciseRecordResponse(
                record.getId(),
                record.getRoutineExercise() == null ? null : record.getRoutineExercise().getId(),
                record.getExercise().getCode(),
                record.getExercise().getName(),
                record.getExercise().getMeasureType(),
                record.getMeasurementStatus(),
                record.getValidCount(),
                record.getBelowStandardCount(),
                record.getUndeterminedCount(),
                record.getActualSeconds(),
                record.getPerformedSets(),
                record.getPostureResult());
    }
}
