package com.team20.fitmate.domain.routine.recommend;

import com.team20.fitmate.domain.routine.entity.Goal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link DefaultRoutineRecommender}가 목표마다 규칙에 맞는 기본 루틴을 돌려주는지 확인하는 테스트.
 */
class DefaultRoutineRecommenderTest {

    private final DefaultRoutineRecommender recommender = new DefaultRoutineRecommender();

    @ParameterizedTest
    @EnumSource(Goal.class)
    @DisplayName("모든 목표에 운동 2~3개와 양수의 수행량·세트를 추천한다")
    void everyGoalHasRoutine(Goal goal) {
        List<RecommendedExercise> recommended = recommender.recommendFirstRound(goal);

        assertThat(recommended).hasSizeBetween(2, 3);
        assertThat(recommended).allSatisfy(item -> {
            assertThat(item.amount()).isPositive();
            assertThat(item.sets()).isPositive();
        });
        assertThat(recommended).extracting(RecommendedExercise::exerciseCode).doesNotHaveDuplicates();
    }
}
