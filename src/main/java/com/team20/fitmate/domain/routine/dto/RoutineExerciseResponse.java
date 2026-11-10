package com.team20.fitmate.domain.routine.dto;

import com.team20.fitmate.domain.exercise.entity.MeasureType;
import com.team20.fitmate.domain.routine.entity.RoutineExercise;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 루틴을 이루는 운동 하나의 응답.
 *
 * @param routineExerciseId 루틴 구성 운동 식별값 (운동 종료 때 보낸다)
 * @param exerciseId    운동 ID
 * @param exerciseCode  운동 코드 (프론트 자세 인식·가이드 선택용)
 * @param name          운동 이름
 * @param tag           운동 설명 태그. 없으면 null
 * @param measureType   측정 방식
 * @param sortOrder     순서 (1부터)
 * @param targetCount   목표 횟수. 시간형이면 null
 * @param targetSeconds 목표 시간(초). 횟수형이면 null
 * @param targetSets    세트 수
 */
public record RoutineExerciseResponse(

        @Schema(description = "루틴 구성 운동 식별값. 운동 종료 때 수행 기록의 routineExerciseId로 보낸다", example = "1")
        Long routineExerciseId,

        @Schema(description = "운동 ID", example = "1")
        Long exerciseId,

        @Schema(description = "운동 코드. 프론트 자세 인식·가이드 선택에 쓴다 "
                + "(SQUAT, HIP_HINGE_DEADLIFT, LUNGE, PLANK, JUMPING_JACK, HIGH_KNEES)", example = "SQUAT")
        String exerciseCode,

        @Schema(description = "운동 이름", example = "스쿼트")
        String name,

        @Schema(description = "운동 설명 태그", example = "자세 개선 · 하체 근력 강화", nullable = true)
        String tag,

        @Schema(description = "측정 방식 (COUNT 횟수형, TIME 시간형)", example = "COUNT")
        MeasureType measureType,

        @Schema(description = "순서 (1부터)", example = "1")
        int sortOrder,

        @Schema(description = "목표 횟수. 시간형이면 null", example = "15", nullable = true)
        Integer targetCount,

        @Schema(description = "목표 시간(초). 횟수형이면 null", example = "30", nullable = true)
        Integer targetSeconds,

        @Schema(description = "세트 수", example = "3")
        int targetSets
) {

    /**
     * 구성 운동 엔티티를 응답으로 바꾼다. 운동(LAZY)을 읽으므로 트랜잭션 안에서 부른다.
     */
    public static RoutineExerciseResponse from(RoutineExercise routineExercise) {
        return new RoutineExerciseResponse(
                routineExercise.getId(),
                routineExercise.getExercise().getId(),
                routineExercise.getExercise().getCode(),
                routineExercise.getExercise().getName(),
                routineExercise.getTag(),
                routineExercise.getExercise().getMeasureType(),
                routineExercise.getSortOrder(),
                routineExercise.getTargetCount(),
                routineExercise.getTargetSeconds(),
                routineExercise.getTargetSets()
        );
    }
}
