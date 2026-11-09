package com.team20.fitmate.domain.xp.dto;

import com.team20.fitmate.domain.room.entity.Team;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 팀 누적 경험치 응답.
 *
 * @param teamId          팀 식별값
 * @param teamName        팀 이름
 * @param formulaDecided  경험치 산식이 정해졌는지
 * @param totalXp         팀 누적 경험치. 산식이 정해지지 않았으면 null
 * @param level           팀 레벨. 레벨 기준 합의 전이라 null
 */
public record TeamXpResponse(

        @Schema(description = "팀 식별값", example = "1")
        Long teamId,

        @Schema(description = "팀 이름", example = "FITMATE 팀")
        String teamName,

        @Schema(description = "경험치 산식이 정해졌는지. false면 경험치 수치를 보여주지 않는다", example = "true")
        boolean formulaDecided,

        @Schema(description = "팀 누적 경험치. 산식이 정해지지 않았으면 null", example = "320", nullable = true)
        Integer totalXp,

        @Schema(description = "팀 레벨. 레벨 기준 합의 전이라 null", nullable = true)
        Integer level
) {

    /**
     * 팀과 누적 경험치로 응답을 만든다. 산식이 정해지지 않았으면 수치를 비운다.
     */
    public static TeamXpResponse of(Team team, boolean formulaDecided, int totalXp) {
        return new TeamXpResponse(team.getId(), team.getName(), formulaDecided,
                formulaDecided ? totalXp : null, team.getLevel());
    }
}
