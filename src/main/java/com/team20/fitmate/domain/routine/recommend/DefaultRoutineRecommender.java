package com.team20.fitmate.domain.routine.recommend;

import com.team20.fitmate.domain.routine.entity.Goal;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * 목표별로 정해 둔 기본 루틴을 돌려주는 추천 규칙.
 * <p>
 * <b>임시 규칙이다.</b> 운동 구성과 수행량은 AI 담당이 확정하면 이 표를 바꾼다.
 * 자세 개선은 매니패스트 와이어프레임의 "이전 회차" 값(스쿼트 3세트×10회, 플랭크 3세트×20초)을 따랐고,
 * 나머지 목표는 같은 운동 범위 안에서 임시로 구성했다.
 * 1회차는 사전 체력 테스트 없이 이 기본 루틴으로 시작한다.
 */
@Component
public class DefaultRoutineRecommender implements RoutineRecommender {

    /**
     * 목표별 기본 루틴 (임시). 프론트 임시 루틴(fitmate-fe {@code config/provisional.ts})과 같은 값이다.
     * 운동 인식은 프론트(AI)가 하므로, 인식할 수 있는 운동과 수행량은 프론트 쪽 값을 따른다.
     * PM·AI가 확정하면 이 값만 바꾼다.
     */
    private static final Map<Goal, List<RecommendedExercise>> DEFAULT_ROUTINES = Map.of(
            Goal.WEIGHT_LOSS, List.of(
                    new RecommendedExercise("JUMPING_JACK", 20, 3, "체중 감량 · 전신 유산소"),
                    new RecommendedExercise("SQUAT", 12, 3, "체중 감량 · 하체 근력"),
                    new RecommendedExercise("LUNGE", 10, 2, "체중 감량 · 하체 균형")),
            Goal.POSTURE, List.of(
                    new RecommendedExercise("SQUAT", 12, 3, "자세 개선 · 하체 근력 강화"),
                    new RecommendedExercise("HIP_HINGE_DEADLIFT", 10, 3, "자세 개선 · 후면 사슬 강화"),
                    new RecommendedExercise("PLANK", 30, 3, "코어 안정화 · 자세 유지력")),
            Goal.STRENGTH, List.of(
                    new RecommendedExercise("SQUAT", 15, 3, "근력 강화 · 하체"),
                    new RecommendedExercise("LUNGE", 12, 3, "근력 강화 · 하체 균형"),
                    new RecommendedExercise("PLANK", 30, 3, "근력 강화 · 코어")),
            Goal.STAMINA, List.of(
                    new RecommendedExercise("JUMPING_JACK", 25, 3, "체력 증진 · 유산소"),
                    new RecommendedExercise("HIGH_KNEES", 20, 3, "체력 증진 · 인터벌"),
                    new RecommendedExercise("SQUAT", 15, 2, "체력 증진 · 하체 지구력"))
    );

    /**
     * 목표에 정해 둔 기본 루틴을 돌려준다. 정해 둔 루틴이 없으면 빈 목록이다.
     */
    @Override
    public List<RecommendedExercise> recommendFirstRound(Goal goal) {
        return DEFAULT_ROUTINES.getOrDefault(goal, List.of());
    }
}
