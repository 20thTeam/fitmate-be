package com.team20.fitmate.domain.contest.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * 한 판 결과 제출 요청. 카메라 영상은 보내지 않고 횟수만 보낸다.
 *
 * @param playerId join 응답에서 받은 판 참가 식별값
 * @param reps     스쿼트 횟수. 0 이상 정수, 측정 안 됨(자세가 한 번도 인식되지 않음)이면 null
 */
public record ContestResultRequest(

        @Schema(description = "join 응답의 playerId", example = "41", type = "string")
        @NotNull(message = "playerId를 보내 주세요.")
        Long playerId,

        @Schema(description = "스쿼트 횟수. 0 이상 정수. 측정 안 됨이면 null(0으로 채우지 않는다)",
                example = "21", nullable = true)
        @Min(value = 0, message = "횟수는 0 이상이어야 합니다.")
        Integer reps
) {
}
