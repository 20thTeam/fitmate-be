package com.team20.fitmate.domain.xp.dto;

import com.team20.fitmate.domain.room.entity.Team;
import com.team20.fitmate.domain.xp.entity.TeamLevel;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 팀 누적 경험치와 레벨 응답. 운동방의 팀 레벨 표시와 성장 기록의 다음 레벨 진행도에 쓴다.
 *
 * @param teamId         팀 식별값
 * @param teamName       팀 이름
 * @param formulaDecided 경험치 산식이 정해졌는지
 * @param totalXp        팀 누적 경험치
 * @param level          팀 레벨 (1~5)
 * @param levelMinXp     지금 레벨의 시작 경험치
 * @param nextLevelXp    다음 레벨의 시작 경험치. 최고 레벨이면 null
 * @param maxLevel       최고 레벨인지
 */
public record TeamXpResponse(

        @Schema(description = "팀 식별값", example = "1")
        Long teamId,

        @Schema(description = "팀 이름", example = "FITMATE 팀")
        String teamName,

        @Schema(description = "경험치 산식이 정해졌는지. false면 아래 수치가 모두 null이다", example = "true")
        boolean formulaDecided,

        @Schema(description = "팀 누적 경험치", example = "320", nullable = true)
        Integer totalXp,

        @Schema(description = "팀 레벨 (1~5)", example = "3", nullable = true)
        Integer level,

        @Schema(description = "지금 레벨의 시작 경험치. 진행도 = (totalXp - levelMinXp) / (nextLevelXp - levelMinXp)",
                example = "300", nullable = true)
        Integer levelMinXp,

        @Schema(description = "다음 레벨의 시작 경험치. 최고 레벨(Lv.5)이면 null", example = "550", nullable = true)
        Integer nextLevelXp,

        @Schema(description = "최고 레벨인지", example = "false", nullable = true)
        Boolean maxLevel
) {

    /**
     * 팀과 누적 경험치로 응답을 만든다. 산식이 정해지지 않았으면 수치를 비운다.
     */
    public static TeamXpResponse of(Team team, boolean formulaDecided, int totalXp) {
        if (!formulaDecided) {
            return new TeamXpResponse(team.getId(), team.getName(), false, null, null, null, null, null);
        }
        TeamLevel level = TeamLevel.of(totalXp);
        return new TeamXpResponse(team.getId(), team.getName(), true, totalXp, level.level(), level.levelMinXp(),
                level.nextLevelXp(), level.isMax());
    }
}
