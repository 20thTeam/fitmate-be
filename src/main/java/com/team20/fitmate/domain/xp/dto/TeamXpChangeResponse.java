package com.team20.fitmate.domain.xp.dto;

import com.team20.fitmate.domain.room.entity.Team;
import com.team20.fitmate.domain.xp.entity.XpStatus;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 운동 하나로 달라진 팀 경험치. 적립됐을 때만 수치를 채운다.
 *
 * @param status   경험치 상태
 * @param teamId   팀 식별값. 팀이 없으면 null
 * @param teamName 팀 이름. 팀이 없으면 null
 * @param earnedXp 이번 운동으로 적립된 경험치. 적립되지 않았으면 null
 * @param totalXp  팀 누적 경험치. 적립되지 않았으면 null
 * @param level    팀 레벨. 레벨 기준 합의 전이라 항상 null
 */
public record TeamXpChangeResponse(

        @Schema(description = "경험치 상태 (ACCRUED 적립됨, FORMULA_UNDECIDED 산식 미정, NO_TEAM 팀 없음, "
                + "NOT_FINISHED 운동 진행 중). ACCRUED일 때만 수치를 보여준다", example = "ACCRUED")
        XpStatus status,

        @Schema(description = "팀 식별값. 팀이 없으면 null", example = "1", nullable = true)
        Long teamId,

        @Schema(description = "팀 이름. 팀이 없으면 null", example = "FITMATE 팀", nullable = true)
        String teamName,

        @Schema(description = "이번 운동으로 적립된 경험치. 적립되지 않았으면 null", example = "30", nullable = true)
        Integer earnedXp,

        @Schema(description = "팀 누적 경험치. 적립되지 않았으면 null", example = "320", nullable = true)
        Integer totalXp,

        @Schema(description = "팀 레벨. 레벨 기준 합의 전이라 null", nullable = true)
        Integer level
) {

    /** 적립된 경험치 변화 */
    public static TeamXpChangeResponse accrued(Team team, int earnedXp, int totalXp) {
        return new TeamXpChangeResponse(XpStatus.ACCRUED, team.getId(), team.getName(), earnedXp, totalXp,
                team.getLevel());
    }

    /** 적립하지 않은 상태. 수치는 비워 둔다 */
    public static TeamXpChangeResponse notAccrued(XpStatus status, Team team) {
        return new TeamXpChangeResponse(status, team == null ? null : team.getId(),
                team == null ? null : team.getName(), null, null, null);
    }
}
