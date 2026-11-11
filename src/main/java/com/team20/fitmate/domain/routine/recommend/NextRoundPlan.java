package com.team20.fitmate.domain.routine.recommend;

import com.team20.fitmate.domain.routine.entity.AdjustmentType;

import java.util.List;

/**
 * 다음 회차 추천 결과. 운동 구성과 순서, 세트는 이전 회차와 같고 수행량만 바뀔 수 있다.
 *
 * @param adjustmentType 전체 조정 유형
 * @param exercises      이전 회차와 같은 순서의 운동별 다음 수행량과 사유
 */
public record NextRoundPlan(AdjustmentType adjustmentType, List<AdjustedExercise> exercises) {

    /**
     * 다음 회차 운동 하나.
     *
     * @param amount 횟수형이면 목표 횟수, 시간형이면 목표 시간(초)
     * @param reason 이렇게 정한 이유 (예: "스쿼트: 목표의 100%를 수행해 12회 → 14회로 늘렸어요.")
     */
    public record AdjustedExercise(int amount, String reason) {
    }
}
