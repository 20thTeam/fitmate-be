package com.team20.fitmate.domain.contest.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

/**
 * 판 시작·나가기 요청. 누가 눌렀는지만 받는다.
 *
 * @param playerId join 응답에서 받은 판 참가 식별값 (문자열 "41"과 숫자 41 모두 받는다)
 */
public record ContestPlayerRequest(

        @Schema(description = "join 응답의 playerId", example = "41", type = "string")
        @NotNull(message = "playerId를 보내 주세요.")
        Long playerId
) {
}
