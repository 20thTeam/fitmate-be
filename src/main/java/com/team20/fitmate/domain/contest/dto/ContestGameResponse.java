package com.team20.fitmate.domain.contest.dto;

import com.team20.fitmate.domain.contest.entity.ContestGame;
import com.team20.fitmate.domain.contest.entity.ContestGameStatus;
import com.team20.fitmate.domain.contest.entity.ContestPlayer;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.List;

/**
 * 판 상태 응답 (이슈 #34의 Game).
 * <p>
 * 식별값은 문자열로, 시각은 ISO 8601(UTC, 밀리초 포함) 문자열로 내려준다.
 * 노트북마다 시계가 달라서 프론트가 {@code serverNow}로 시계를 맞추므로 모든 응답에 넣는다.
 *
 * @param gameId          판 식별값
 * @param status          판 상태
 * @param startAt         운동 시작 시각(카운트다운이 끝나는 순간). 시작 전이면 null
 * @param durationSeconds 운동 시간(초)
 * @param maxPlayers      최대 인원
 * @param serverNow       응답을 만든 서버 시각
 * @param players         판에 들어온 사람들 (들어온 순)
 */
public record ContestGameResponse(

        @Schema(description = "판 식별값", example = "17")
        String gameId,

        @Schema(description = "WAITING(모집 중) → COUNTDOWN(startAt 전) → RUNNING(운동 중·제출 대기) → FINISHED",
                example = "WAITING")
        ContestGameStatus status,

        @Schema(description = "운동 시작 시각(ISO 8601). 시작 전이면 null", example = "2026-11-13T05:00:05.000Z",
                nullable = true)
        String startAt,

        @Schema(description = "운동 시간(초)", example = "20")
        int durationSeconds,

        @Schema(description = "최대 인원", example = "4")
        int maxPlayers,

        @Schema(description = "응답을 만든 서버 시각(ISO 8601). 프론트가 시계를 맞추는 기준", example = "2026-11-13T05:00:00.000Z")
        String serverNow,

        @Schema(description = "판에 들어온 사람들 (들어온 순)")
        List<Player> players
) {

    /**
     * 판 엔티티와 현재 시각으로 응답을 만든다. 상태는 현재 시각 기준으로 계산한다.
     */
    public static ContestGameResponse from(ContestGame game, Instant now) {
        return new ContestGameResponse(
                String.valueOf(game.getId()),
                game.statusAt(now),
                ContestTimeFormat.format(game.getStartAt()),
                ContestGame.DURATION_SECONDS,
                ContestGame.MAX_PLAYERS,
                ContestTimeFormat.format(now),
                game.getPlayers().stream().map(Player::from).toList());
    }

    /**
     * 판에 들어온 사람 한 명.
     *
     * @param playerId  판 참가 식별값
     * @param nickname  닉네임
     * @param schoolId  학교 식별값
     * @param reps      한 판 횟수. 아직 안 냈거나 측정 안 됨이면 null
     * @param submitted 결과를 냈는지 여부
     */
    public record Player(
            @Schema(description = "판 참가 식별값", example = "41")
            String playerId,

            @Schema(description = "닉네임", example = "참가자1")
            String nickname,

            @Schema(description = "학교 식별값", example = "seoultech")
            String schoolId,

            @Schema(description = "한 판 횟수. 안 냈거나 측정 안 됨이면 null", example = "21", nullable = true)
            Integer reps,

            @Schema(description = "결과를 냈는지 여부", example = "false")
            boolean submitted
    ) {

        /**
         * 판 참가 엔티티를 응답으로 바꾼다.
         */
        public static Player from(ContestPlayer player) {
            return new Player(String.valueOf(player.getId()), player.getNickname(), player.getSchoolId(),
                    player.getReps(), player.isSubmitted());
        }
    }
}
