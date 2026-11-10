package com.team20.fitmate.domain.xp.dto;

import com.team20.fitmate.domain.room.entity.Team;
import com.team20.fitmate.domain.xp.entity.TeamLevel;
import com.team20.fitmate.domain.xp.entity.XpStatus;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 운동 하나로 달라진 팀 경험치. 결과 화면용이며, 적립됐을 때만 수치를 채운다.
 *
 * @param status    경험치 상태
 * @param teamId    팀 식별값. 팀이 없으면 null
 * @param teamName  팀 이름. 팀이 없으면 null
 * @param earnedXp  이번 운동으로 적립된 경험치 (하루 최대치를 넘으면 0일 수 있다)
 * @param totalXp   지금 팀 누적 경험치
 * @param level     지금 팀 레벨
 * @param leveledUp 이번 운동으로 팀 레벨이 올랐는지
 */
public record TeamXpChangeResponse(

        @Schema(description = "경험치 상태 (ACCRUED 적립됨, FORMULA_UNDECIDED 산식 미정, NO_TEAM 팀 없음, "
                + "NOT_FINISHED 운동 진행 중). ACCRUED일 때만 수치를 보여준다", example = "ACCRUED")
        XpStatus status,

        @Schema(description = "팀 식별값. 팀이 없으면 null", example = "1", nullable = true)
        Long teamId,

        @Schema(description = "팀 이름. 팀이 없으면 null", example = "FITMATE 팀", nullable = true)
        String teamName,

        @Schema(description = "이번 운동으로 적립된 경험치. 하루 최대 100XP를 넘으면 0일 수 있다", example = "50", nullable = true)
        Integer earnedXp,

        @Schema(description = "지금 팀 누적 경험치", example = "320", nullable = true)
        Integer totalXp,

        @Schema(description = "지금 팀 레벨 (1~5)", example = "3", nullable = true)
        Integer level,

        @Schema(description = "이번 운동으로 팀 레벨이 올랐는지", example = "true", nullable = true)
        Boolean leveledUp
) {

    /**
     * 적립된 경험치 변화. 레벨 상승은 적립 직후 누적과 적립 전 누적의 레벨을 비교한다.
     */
    public static TeamXpChangeResponse accrued(Team team, int earnedXp, int totalXp, Integer totalXpAfterAccrual) {
        boolean leveledUp = totalXpAfterAccrual != null
                && TeamLevel.of(totalXpAfterAccrual).level() > TeamLevel.of(totalXpAfterAccrual - earnedXp).level();
        return new TeamXpChangeResponse(XpStatus.ACCRUED, team.getId(), team.getName(), earnedXp, totalXp,
                TeamLevel.of(totalXp).level(), leveledUp);
    }

    /** 적립하지 않은 상태. 수치는 비워 둔다 */
    public static TeamXpChangeResponse notAccrued(XpStatus status, Team team) {
        return new TeamXpChangeResponse(status, team == null ? null : team.getId(),
                team == null ? null : team.getName(), null, null, null, null);
    }
}
