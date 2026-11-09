package com.team20.fitmate.domain.workout.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import java.util.List;

/**
 * 운동 종료 요청. 운동별 수행 기록을 한 번에 보낸다.
 * <p>
 * 루틴의 운동 중 보내지 않은 운동은 "측정 못 함"으로 저장한다. 빈 목록이면 모든 운동이 측정 못 함이다.
 *
 * @param records 운동별 수행 기록 (형식은 docs/수행기록_형식.md)
 */
public record WorkoutFinishRequest(

        @Schema(description = "운동별 수행 기록. 보내지 않은 운동은 측정 못 함(NOT_MEASURED)으로 저장한다")
        @NotNull(message = "수행 기록 목록을 보내 주세요. 없으면 빈 목록을 보내 주세요.")
        List<@NotNull(message = "수행 기록 항목이 비어 있습니다.") @Valid ExerciseRecordRequest> records
) {
}
