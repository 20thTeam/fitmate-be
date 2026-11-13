package com.team20.fitmate.domain.contest.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * 학교별 누적·상위 기록 현황 응답 (이슈 #34의 Standings).
 * <p>
 * 끝난(FINISHED) 판에서 측정된(reps가 null이 아닌) 기록만 쓴다.
 *
 * @param schools    학교별 합계. totalReps 큰 순
 * @param topPlayers 한 판 기록 상위 5개. reps 큰 순
 * @param gameCount  측정된 기록이 하나라도 있는 끝난 판 수
 */
public record ContestStandingsResponse(

        @Schema(description = "학교별 합계 (totalReps 큰 순)")
        List<School> schools,

        @Schema(description = "한 판 기록 상위 5개 (reps 큰 순). 같은 사람이 여러 판 하면 여러 줄일 수 있다")
        List<TopPlayer> topPlayers,

        @Schema(description = "측정된 기록이 하나라도 있는 끝난 판 수", example = "5")
        int gameCount
) {

    /**
     * 학교 하나의 누적 기록.
     *
     * @param schoolId   학교 식별값
     * @param totalReps  측정된 기록의 횟수 합계
     * @param entryCount 측정된 기록 수
     */
    public record School(
            @Schema(description = "학교 식별값", example = "seoultech")
            String schoolId,

            @Schema(description = "횟수 합계", example = "132")
            long totalReps,

            @Schema(description = "측정된 결과를 낸 횟수", example = "9")
            int entryCount
    ) {
    }

    /**
     * 한 판 기록 하나.
     *
     * @param nickname 닉네임
     * @param schoolId 학교 식별값
     * @param reps     한 판 횟수
     */
    public record TopPlayer(
            @Schema(description = "닉네임", example = "참가자1")
            String nickname,

            @Schema(description = "학교 식별값", example = "seoultech")
            String schoolId,

            @Schema(description = "한 판 횟수", example = "21")
            int reps
    ) {
    }
}
