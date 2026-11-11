package com.team20.fitmate.domain.routine.dto;

import com.team20.fitmate.domain.routine.entity.AdjustmentType;
import com.team20.fitmate.domain.routine.entity.Goal;
import com.team20.fitmate.domain.routine.entity.Routine;
import com.team20.fitmate.domain.routine.entity.RoutineStatus;
import com.team20.fitmate.domain.workout.entity.Workout;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * 루틴 응답. 루틴 확인 화면과 운동방에서 같은 순서·수행량을 쓴다.
 *
 * @param routineId       루틴 식별값
 * @param roundNo         회차
 * @param goal            목표
 * @param goalName        목표 한글 이름
 * @param status          추천 상태
 * @param exercises       구성 운동 (순서대로). 추천 완료가 아니면 빈 목록
 * @param previousRoutineId 이전 회차 루틴 식별값. 첫 회차는 null
 * @param basedOnWorkoutId  근거가 된 이전 회차 운동 기록 식별값. 첫 회차는 null
 * @param adjustmentType    조정 유형. 첫 회차는 null
 * @param adjustmentReasons 운동별 조정 사유 (순서대로). 첫 회차는 빈 목록
 */
public record RoutineResponse(

        @Schema(description = "루틴 식별값", example = "1")
        Long routineId,

        @Schema(description = "회차", example = "1")
        int roundNo,

        @Schema(description = "목표", example = "STRENGTH")
        Goal goal,

        @Schema(description = "목표 한글 이름", example = "근력 강화")
        String goalName,

        @Schema(description = "추천 상태 (PREPARING 준비, COMPLETED 완료, UNAVAILABLE 불가)", example = "COMPLETED")
        RoutineStatus status,

        @Schema(description = "구성 운동 (순서대로). 추천 완료가 아니면 빈 목록")
        List<RoutineExerciseResponse> exercises,

        @Schema(description = "이전 회차 루틴 식별값. 첫 회차는 null", example = "1")
        Long previousRoutineId,

        @Schema(description = "근거가 된 이전 회차 운동 기록 식별값. 첫 회차는 null", example = "3")
        Long basedOnWorkoutId,

        @Schema(description = "조정 유형 (UP 상향, KEEP 유지, UNAVAILABLE 분석 불가). 첫 회차는 null", example = "UP")
        AdjustmentType adjustmentType,

        @Schema(description = "운동별 조정 사유 (순서대로). 첫 회차는 빈 목록",
                example = "[\"스쿼트: 목표의 100%를 수행해 12회 → 14회로 늘렸어요.\"]")
        List<String> adjustmentReasons
) {

    /**
     * 루틴 엔티티를 응답으로 바꾼다. 구성 운동(LAZY)을 읽으므로 트랜잭션 안에서 부른다.
     */
    public static RoutineResponse from(Routine routine) {
        List<RoutineExerciseResponse> exercises = routine.getStatus() == RoutineStatus.COMPLETED
                ? routine.getRoutineExercises().stream().map(RoutineExerciseResponse::from).toList()
                : List.of();
        Workout basedOn = routine.getBasedOnWorkout();
        return new RoutineResponse(
                routine.getId(),
                routine.getRoundNo(),
                routine.getGoal(),
                routine.getGoal().getDisplayName(),
                routine.getStatus(),
                exercises,
                basedOn == null ? null : basedOn.getRoutine().getId(),
                basedOn == null ? null : basedOn.getId(),
                routine.getAdjustmentType(),
                routine.getAdjustmentReasons()
        );
    }
}
