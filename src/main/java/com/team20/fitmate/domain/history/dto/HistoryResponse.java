package com.team20.fitmate.domain.history.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * 내 성장 기록. 기록이 없으면 누적량은 0·null이고 회차 목록은 빈 목록이다.
 *
 * @param summary  누적량
 * @param workouts 회차별 기록 (최근 종료 순)
 */
public record HistoryResponse(

        @Schema(description = "누적량")
        HistorySummary summary,

        @Schema(description = "회차별 기록 (최근 종료 순). 기록이 없으면 빈 목록")
        List<HistoryWorkoutResponse> workouts
) {
}
