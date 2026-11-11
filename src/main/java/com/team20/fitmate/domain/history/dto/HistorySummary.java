package com.team20.fitmate.domain.history.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 성장 기록 누적량. 실제 저장된 종료 기록만 더하고, 근거가 없는 값은 0이 아니라 null로 둔다.
 *
 * @param workoutCount         종료한 운동 수
 * @param totalElapsedSeconds  시작부터 종료까지 걸린 시간의 합(초)
 * @param totalExerciseSeconds 운동별 실제 소요 시간의 합(초). 보낸 값이 하나도 없으면 null
 * @param totalValidCount      측정된 운동의 유효 횟수 합. 측정된 횟수형 운동이 하나도 없으면 null
 * @param totalEarnedXp        팀에 적립한 경험치 합. 경험치 산식이 미정이면 null
 */
public record HistorySummary(

        @Schema(description = "종료한 운동 수", example = "2")
        int workoutCount,

        @Schema(description = "시작부터 종료까지 걸린 시간의 합(초). 쉬는 시간이 포함된다", example = "1080")
        long totalElapsedSeconds,

        @Schema(description = "운동별 실제 소요 시간의 합(초). 보낸 값이 하나도 없으면 null", example = "840")
        Integer totalExerciseSeconds,

        @Schema(description = "측정된 운동의 유효 횟수 합. 측정된 횟수형 운동이 하나도 없으면 null(0이 아님)", example = "140")
        Integer totalValidCount,

        @Schema(description = "팀에 적립한 경험치 합. 경험치 산식이 미정이면 null", example = "181")
        Integer totalEarnedXp
) {
}
