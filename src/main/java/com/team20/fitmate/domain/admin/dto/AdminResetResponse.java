package com.team20.fitmate.domain.admin.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 시연 데이터 초기화 결과.
 *
 * @param deletedParticipants 지운 참가자 수
 * @param deletedWorkouts     지운 운동 기록 수 (진행 중 포함)
 * @param resetTeams          경험치를 0으로 되돌린 팀 수
 */
public record AdminResetResponse(

        @Schema(description = "지운 참가자 수", example = "14")
        int deletedParticipants,

        @Schema(description = "지운 운동 기록 수 (진행 중 포함)", example = "22")
        int deletedWorkouts,

        @Schema(description = "경험치를 0으로 되돌린 팀 수", example = "1")
        int resetTeams
) {
}
