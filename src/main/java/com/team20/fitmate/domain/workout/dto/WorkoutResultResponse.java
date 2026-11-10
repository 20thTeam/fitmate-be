package com.team20.fitmate.domain.workout.dto;

import com.team20.fitmate.domain.xp.dto.TeamXpChangeResponse;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 운동 결과 응답. 결과 화면에서 내 수행 기록과 팀 경험치 변화를 함께 보여준다.
 *
 * @param workout 운동 기록과 확정된 수행 기록
 * @param teamXp  이번 운동으로 달라진 팀 경험치
 */
public record WorkoutResultResponse(

        @Schema(description = "운동 기록과 확정된 운동별 수행 기록")
        WorkoutResponse workout,

        @Schema(description = "이번 운동으로 달라진 팀 경험치. status가 ACCRUED일 때만 수치가 있다")
        TeamXpChangeResponse teamXp
) {
}
