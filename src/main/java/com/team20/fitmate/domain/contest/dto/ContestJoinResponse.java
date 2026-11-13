package com.team20.fitmate.domain.contest.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 판 참가 응답. 이후 시작·결과·나가기 요청에 쓸 playerId와 들어간 판 상태를 함께 준다.
 *
 * @param playerId 판 참가 식별값
 * @param game     들어간 판 상태
 */
public record ContestJoinResponse(

        @Schema(description = "판 참가 식별값. 시작·결과·나가기 요청에 보낸다", example = "41")
        String playerId,

        @Schema(description = "들어간 판 상태")
        ContestGameResponse game
) {
}
