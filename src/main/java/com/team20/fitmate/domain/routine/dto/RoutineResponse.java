package com.team20.fitmate.domain.routine.dto;

import com.team20.fitmate.domain.routine.entity.Goal;
import com.team20.fitmate.domain.routine.entity.Routine;
import com.team20.fitmate.domain.routine.entity.RoutineStatus;
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
        List<RoutineExerciseResponse> exercises
) {

    /**
     * 루틴 엔티티를 응답으로 바꾼다. 구성 운동(LAZY)을 읽으므로 트랜잭션 안에서 부른다.
     */
    public static RoutineResponse from(Routine routine) {
        List<RoutineExerciseResponse> exercises = routine.getStatus() == RoutineStatus.COMPLETED
                ? routine.getRoutineExercises().stream().map(RoutineExerciseResponse::from).toList()
                : List.of();
        return new RoutineResponse(
                routine.getId(),
                routine.getRoundNo(),
                routine.getGoal(),
                routine.getGoal().getDisplayName(),
                routine.getStatus(),
                exercises
        );
    }
}
