package com.team20.fitmate.domain.workout.dto;

import com.team20.fitmate.domain.workout.entity.ExerciseRecord;
import com.team20.fitmate.domain.workout.entity.MeasurementStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import tools.jackson.databind.JsonNode;

/**
 * 공개 파티 운동 종료 때 보내는 본인 수행 기록. 공개 파티 동작은 서버가 정하므로(점핑잭) 운동 식별값은 받지 않는다.
 * <p>
 * 규칙은 루틴 운동의 수행 기록과 같다. 측정하지 못했으면 값을 null로 두고 {@code NOT_MEASURED}로 보낸다.
 *
 * @param measurementStatus  측정 상태
 * @param validCount         유효 횟수 (측정됨이면 필수)
 * @param belowStandardCount 기준 미달 횟수
 * @param undeterminedCount  판정 불가 횟수
 * @param actualSeconds      실제 운동 시간(초, 일시정지 제외)
 * @param postureResult      자세 판정 결과 (형식 미정, JSON 그대로)
 */
public record PublicPartyFinishRequest(

        @Schema(description = "측정 상태 (MEASURED 측정됨, NOT_MEASURED 측정 못 함)", example = "MEASURED")
        @NotNull(message = "측정 상태를 알려 주세요.")
        MeasurementStatus measurementStatus,

        @Schema(description = "유효 횟수. 측정됨이면 필수, 미측정은 null", example = "48", nullable = true)
        @PositiveOrZero(message = "유효 횟수는 0 이상이어야 합니다.")
        Integer validCount,

        @Schema(description = "기준 미달 횟수. 미측정은 null", example = "3", nullable = true)
        @PositiveOrZero(message = "기준 미달 횟수는 0 이상이어야 합니다.")
        Integer belowStandardCount,

        @Schema(description = "판정 불가 횟수. 미측정은 null", example = "1", nullable = true)
        @PositiveOrZero(message = "판정 불가 횟수는 0 이상이어야 합니다.")
        Integer undeterminedCount,

        @Schema(description = "실제 운동 시간(초, 일시정지 제외). 모르면 null", example = "600", nullable = true)
        @PositiveOrZero(message = "운동 시간은 0 이상이어야 합니다.")
        Integer actualSeconds,

        @Schema(description = "자세 판정 결과. 형식 미정이라 AI가 만든 JSON을 그대로 보낸다. 미측정은 null",
                example = "{\"feedback\": [\"팔을 머리 위까지 올려 주세요\"]}", nullable = true)
        JsonNode postureResult
) {

    /**
     * 엔티티에 담을 값 묶음으로 바꾼다. 세트 구분이 없는 운동이라 수행 세트 수는 null이다.
     */
    public ExerciseRecord.Values toValues() {
        String posture = postureResult == null || postureResult.isNull() ? null : postureResult.toString();
        return new ExerciseRecord.Values(measurementStatus, validCount, belowStandardCount, undeterminedCount,
                actualSeconds, null, posture);
    }
}
