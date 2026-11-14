package com.team20.fitmate.domain.contest.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 챌린지 기록 초기화 결과 (이슈 #41). 지운 개수만 돌려준다.
 *
 * @param deletedGames   지운 판 수(진행 중인 판 포함)
 * @param deletedPlayers 지운 판 참가자·결과 수
 */
public record ContestResetResponse(

        @Schema(description = "지운 판 수(진행 중인 판 포함)", example = "12")
        int deletedGames,

        @Schema(description = "지운 판 참가자·결과 수", example = "41")
        int deletedPlayers
) {
}
