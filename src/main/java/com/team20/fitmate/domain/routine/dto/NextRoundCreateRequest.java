package com.team20.fitmate.domain.routine.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

/**
 * 다음 회차 루틴 추천 요청.
 *
 * @param workoutId 근거로 쓸 종료된 운동 기록 식별값
 */
public record NextRoundCreateRequest(

        @Schema(description = "근거로 쓸 종료된 운동 기록 식별값 (운동 시작 응답의 workoutId)", example = "3")
        @NotNull(message = "운동 기록을 선택해 주세요.")
        Long workoutId
) {
}
