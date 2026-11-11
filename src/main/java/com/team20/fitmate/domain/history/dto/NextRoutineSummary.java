package com.team20.fitmate.domain.history.dto;

import com.team20.fitmate.domain.routine.entity.AdjustmentType;
import com.team20.fitmate.domain.routine.entity.Routine;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * 한 회차 기록으로 만든 다음 회차 추천 요약. 지난 결과와 다음 추천의 변화를 보여줄 때 쓴다.
 *
 * @param routineId         다음 회차 루틴 식별값
 * @param roundNo           다음 회차 번호
 * @param adjustmentType    조정 유형
 * @param adjustmentReasons 운동별 조정 사유 (순서대로)
 */
public record NextRoutineSummary(

        @Schema(description = "다음 회차 루틴 식별값", example = "7")
        Long routineId,

        @Schema(description = "다음 회차 번호", example = "2")
        int roundNo,

        @Schema(description = "조정 유형 (UP 상향, KEEP 유지, UNAVAILABLE 분석 불가)", example = "UP")
        AdjustmentType adjustmentType,

        @Schema(description = "운동별 조정 사유 (순서대로)")
        List<String> adjustmentReasons
) {

    /**
     * 다음 회차 루틴을 요약으로 바꾼다.
     */
    public static NextRoutineSummary from(Routine routine) {
        return new NextRoutineSummary(routine.getId(), routine.getRoundNo(),
                routine.getAdjustmentType(), routine.getAdjustmentReasons());
    }
}
