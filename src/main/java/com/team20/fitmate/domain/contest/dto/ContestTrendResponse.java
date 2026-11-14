package com.team20.fitmate.domain.contest.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.List;

/**
 * 학교별 누적 기록·순위의 시간대별 추이 응답 (이슈 #43). 태블릿 시연 화면의 추이 그래프가 쓴다.
 * <p>
 * 집계 기준은 현황(standings)과 같다. 끝난(FINISHED) 판에서 측정된(reps가 null이 아닌) 기록만 쓰고,
 * 판은 시작 시각(startAt)이 속한 1시간(한국 시간) 구간에 넣는다.
 *
 * @param buckets   첫 판이 있는 시간부터 지금 시간까지 1시간 구간. 오래된 순. 판이 없으면 빈 목록
 * @param serverNow 응답을 만든 서버 시각
 */
public record ContestTrendResponse(

        @Schema(description = "1시간 구간 목록 (오래된 순). 기록이 없으면 빈 목록. 최대 24개")
        List<Bucket> buckets,

        @Schema(description = "응답을 만든 서버 시각(ISO 8601)", example = "2026-11-15T05:00:00.000Z")
        String serverNow
) {

    public static ContestTrendResponse of(List<Bucket> buckets, Instant now) {
        return new ContestTrendResponse(buckets, ContestTimeFormat.format(now));
    }

    /**
     * 1시간 구간 하나. 학교 값은 이 구간이 끝날 때까지의 누적이다.
     *
     * @param startAt 구간 시작 시각(UTC)
     * @param hour    구간 시작 시(한국 시간, 0~23). 화면의 "10시" 라벨
     * @param schools 이 구간까지 기록이 있는 학교. 순위 순
     */
    public record Bucket(
            @Schema(description = "구간 시작 시각(ISO 8601, UTC)", example = "2026-11-15T01:00:00.000Z")
            String startAt,

            @Schema(description = "구간 시작 시(한국 시간 0~23)", example = "10")
            int hour,

            @Schema(description = "이 구간까지 기록이 있는 학교 (rank 순)")
            List<SchoolPoint> schools
    ) {

        public static Bucket of(Instant startAt, int hour, List<SchoolPoint> schools) {
            return new Bucket(ContestTimeFormat.format(startAt), hour, schools);
        }
    }

    /**
     * 한 구간에서 학교 하나의 값.
     *
     * @param schoolId  학교 식별값
     * @param totalReps 이 구간까지 누적 횟수
     * @param hourReps  이 구간 안에서 더해진 횟수
     * @param rank      이 구간 기준 순위(1부터). 누적 큰 순, 같으면 학교 식별값 순이라 겹치지 않는다(standings 순서와 같음)
     */
    public record SchoolPoint(
            @Schema(description = "학교 식별값", example = "catholic")
            String schoolId,

            @Schema(description = "이 구간까지 누적 횟수", example = "132")
            long totalReps,

            @Schema(description = "이 구간 안에서 더해진 횟수", example = "21")
            long hourReps,

            @Schema(description = "순위 (1부터, 겹치지 않음)", example = "1")
            int rank
    ) {
    }
}
