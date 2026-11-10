package com.team20.fitmate.domain.workout.dto;

import com.team20.fitmate.domain.workout.entity.ExerciseRecord;
import com.team20.fitmate.domain.workout.entity.Workout;
import com.team20.fitmate.domain.workout.entity.WorkoutStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 운동 기록 응답.
 *
 * @param workoutId  운동 기록 식별값
 * @param routineId  수행한 루틴 식별값
 * @param roomId     운동한 운동방 식별값. 입장 전이면 null
 * @param status     상태
 * @param startedAt  시작 시각
 * @param finishedAt 종료 시각. 진행 중이면 null
 * @param records    확정된 운동별 수행 기록 (루틴 순서). 진행 중이면 빈 목록
 */
public record WorkoutResponse(

        @Schema(description = "운동 기록 식별값", example = "1")
        Long workoutId,

        @Schema(description = "수행한 루틴 식별값", example = "1", nullable = true)
        Long routineId,

        @Schema(description = "운동한 운동방 식별값. 입장 전이면 null", example = "1", nullable = true)
        Long roomId,

        @Schema(description = "상태 (IN_PROGRESS 진행 중, FINISHED 종료)", example = "IN_PROGRESS")
        WorkoutStatus status,

        @Schema(description = "시작 시각", example = "2026-11-12T14:00:00")
        LocalDateTime startedAt,

        @Schema(description = "종료 시각. 진행 중이면 null", example = "2026-11-12T14:20:00", nullable = true)
        LocalDateTime finishedAt,

        @Schema(description = "확정된 운동별 수행 기록 (루틴 순서). 진행 중이면 빈 목록")
        List<ExerciseRecordResponse> records
) {

    /**
     * 운동 기록과 확정된 수행 기록으로 응답을 만든다.
     */
    public static WorkoutResponse from(Workout workout, List<ExerciseRecord> records) {
        return new WorkoutResponse(
                workout.getId(),
                workout.getRoutine() == null ? null : workout.getRoutine().getId(),
                workout.getRoom() == null ? null : workout.getRoom().getId(),
                workout.getStatus(),
                workout.getStartedAt(),
                workout.getFinishedAt(),
                records.stream().map(ExerciseRecordResponse::from).toList());
    }
}
