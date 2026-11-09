package com.team20.fitmate.domain.workout.dto;

import com.team20.fitmate.domain.workout.entity.ExerciseRecord;
import com.team20.fitmate.domain.workout.entity.MeasurementStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import tools.jackson.databind.JsonNode;

/**
 * 수행 기록 제출 형식(초안). 운동 종료 시 운동마다 하나씩 보낸다.
 * <p>
 * 프론트는 AI 판정 결과를 이 형식으로 모아 보낸다. 측정하지 못한 값은 0이 아니라 null로 보내고
 * {@code measurementStatus}를 {@code NOT_MEASURED}로 둔다. 자세 판정 결과는 형식이 미정이라 JSON을 그대로 받는다.
 * 형식 설명은 {@code docs/수행기록_형식.md}에 있다.
 *
 * @param routineExerciseId  루틴 구성 운동 식별값 (어떤 운동의 결과인지)
 * @param measurementStatus  측정 상태
 * @param validCount         유효 횟수 (횟수형 측정 시 필수)
 * @param belowStandardCount 기준 미달 횟수
 * @param undeterminedCount  판정 불가 횟수
 * @param actualSeconds      실제 소요 시간(초) (시간형 측정 시 필수)
 * @param performedSets      수행 세트 수
 * @param postureResult      자세 판정 결과 (형식 미정, JSON 그대로)
 */
public record ExerciseRecordRequest(

        @Schema(description = "루틴 구성 운동 식별값", example = "1")
        @NotNull(message = "어떤 운동의 기록인지 알려 주세요.")
        Long routineExerciseId,

        @Schema(description = "측정 상태 (MEASURED 측정됨, NOT_MEASURED 측정 못 함)", example = "MEASURED")
        @NotNull(message = "측정 상태를 알려 주세요.")
        MeasurementStatus measurementStatus,

        @Schema(description = "유효 횟수. 횟수형 측정 시 필수, 시간형·미측정은 null", example = "10", nullable = true)
        @PositiveOrZero(message = "유효 횟수는 0 이상이어야 합니다.")
        Integer validCount,

        @Schema(description = "기준 미달 횟수. 미측정은 null", example = "2", nullable = true)
        @PositiveOrZero(message = "기준 미달 횟수는 0 이상이어야 합니다.")
        Integer belowStandardCount,

        @Schema(description = "판정 불가 횟수. 미측정은 null", example = "1", nullable = true)
        @PositiveOrZero(message = "판정 불가 횟수는 0 이상이어야 합니다.")
        Integer undeterminedCount,

        @Schema(description = "실제 소요 시간(초). 시간형 측정 시 필수(유효 유지 시간)", example = "95", nullable = true)
        @PositiveOrZero(message = "소요 시간은 0 이상이어야 합니다.")
        Integer actualSeconds,

        @Schema(description = "수행 세트 수", example = "3", nullable = true)
        @PositiveOrZero(message = "세트 수는 0 이상이어야 합니다.")
        Integer performedSets,

        @Schema(description = "자세 판정 결과. 형식 미정이라 AI가 만든 JSON을 그대로 보낸다. 미측정은 null",
                example = "{\"feedback\": [\"무릎이 발끝을 넘어요\"], \"belowStandardReasons\": {\"KNEE_OVER_TOE\": 2}}",
                nullable = true)
        JsonNode postureResult
) {

    /**
     * 엔티티에 담을 값 묶음으로 바꾼다. 자세 판정 결과는 JSON 문자열로 바꾼다.
     * JSON {@code null}이 오면 결과 없음으로 본다.
     */
    public ExerciseRecord.Values toValues() {
        String posture = postureResult == null || postureResult.isNull() ? null : postureResult.toString();
        return new ExerciseRecord.Values(measurementStatus, validCount, belowStandardCount, undeterminedCount,
                actualSeconds, performedSets, posture);
    }
}
