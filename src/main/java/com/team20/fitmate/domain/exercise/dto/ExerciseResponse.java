package com.team20.fitmate.domain.exercise.dto;

import com.team20.fitmate.domain.exercise.entity.Exercise;
import com.team20.fitmate.domain.exercise.entity.MeasureType;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 운동 하나의 정보와 AI 가이드 시범 참조.
 *
 * @param exerciseId     운동 식별값
 * @param exerciseCode   운동 코드
 * @param name           운동 이름
 * @param measureType    측정 방식
 * @param guideStatus    가이드 시범 상태
 * @param guideMotionRef 가이드 시범 동작 참조. 시범이 없으면 null
 */
public record ExerciseResponse(

        @Schema(description = "운동 식별값", example = "1")
        Long exerciseId,

        @Schema(description = "운동 코드", example = "SQUAT")
        String exerciseCode,

        @Schema(description = "운동 이름", example = "스쿼트")
        String name,

        @Schema(description = "측정 방식 (COUNT 횟수형, TIME 시간형)", example = "COUNT")
        MeasureType measureType,

        @Schema(description = "가이드 시범 상태 (AVAILABLE 있음, NONE 없음). NONE이면 다른 운동의 시범으로 대신하지 않는다",
                example = "NONE")
        GuideStatus guideStatus,

        @Schema(description = "가이드 시범 동작 참조 (3D 담당이 정한 값). 시범이 없으면 null", example = "guide/squat.glb")
        String guideMotionRef
) {

    /**
     * 가이드 시범 상태.
     */
    public enum GuideStatus {
        /** 이 운동의 시범이 있음 */
        AVAILABLE,
        /** 이 운동의 시범이 없음 */
        NONE
    }

    /**
     * 운동 엔티티를 응답으로 바꾼다.
     */
    public static ExerciseResponse from(Exercise exercise) {
        return new ExerciseResponse(
                exercise.getId(),
                exercise.getCode(),
                exercise.getName(),
                exercise.getMeasureType(),
                exercise.hasGuideMotion() ? GuideStatus.AVAILABLE : GuideStatus.NONE,
                exercise.getGuideMotionRef());
    }
}
