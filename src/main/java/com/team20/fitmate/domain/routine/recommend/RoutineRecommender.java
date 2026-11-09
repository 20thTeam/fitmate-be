package com.team20.fitmate.domain.routine.recommend;

import com.team20.fitmate.domain.routine.entity.Goal;

import java.util.List;

/**
 * 루틴 추천 규칙.
 * <p>
 * 규칙은 AI 담당이 설계한다. 규칙이 바뀌면 이 인터페이스의 구현만 바꾸고 API는 그대로 둔다.
 */
public interface RoutineRecommender {

    /**
     * 목표로 첫 회차 운동을 순서대로 고른다.
     *
     * @return 추천 운동 목록. 추천할 수 없으면 빈 목록
     */
    List<RecommendedExercise> recommendFirstRound(Goal goal);
}
