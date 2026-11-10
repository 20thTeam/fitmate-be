package com.team20.fitmate.domain.workout.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

/**
 * 운동 시작 요청.
 *
 * @param routineId 수행할 루틴 식별값 (루틴 확인 화면에서 받은 값)
 */
public record WorkoutStartRequest(

        @Schema(description = "수행할 루틴 식별값", example = "1")
        @NotNull(message = "어떤 루틴으로 운동할지 알려 주세요.")
        Long routineId
) {
}
