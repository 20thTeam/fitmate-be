package com.team20.fitmate.domain.routine.dto;

import com.team20.fitmate.domain.routine.entity.Goal;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

/**
 * 첫 회차 루틴 추천 요청.
 *
 * @param goal 선택한 목표
 */
public record RoutineCreateRequest(

        @Schema(description = "목표 (WEIGHT_LOSS 체중 감량, POSTURE 자세 개선, STRENGTH 근력 강화, STAMINA 체력 증진)",
                example = "STRENGTH")
        @NotNull(message = "목표를 선택해 주세요.")
        Goal goal
) {
}
