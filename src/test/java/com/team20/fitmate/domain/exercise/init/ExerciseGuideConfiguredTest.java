package com.team20.fitmate.domain.exercise.init;

import com.team20.fitmate.domain.exercise.dto.ExerciseResponse;
import com.team20.fitmate.domain.exercise.service.ExerciseService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 설정에 적은 운동만 가이드 시범이 생기고, 적지 않은 운동은 다른 운동의 시범으로 대신하지 않는지 확인하는 테스트.
 */
@SpringBootTest(properties = {
        "exercise-guide.motions.SQUAT=guide/squat.glb",
        "exercise-guide.motions.NO_SUCH_EXERCISE=guide/unknown.glb"
})
class ExerciseGuideConfiguredTest {

    @Autowired
    private ExerciseService exerciseService;

    @Test
    @DisplayName("설정에 적은 운동은 시범 있음과 참조를 응답한다")
    void configured() {
        ExerciseResponse squat = exerciseService.get("SQUAT");

        assertThat(squat.guideStatus()).isEqualTo(ExerciseResponse.GuideStatus.AVAILABLE);
        assertThat(squat.guideMotionRef()).isEqualTo("guide/squat.glb");
    }

    @Test
    @DisplayName("설정에 없는 운동은 시범 없음이고 다른 운동의 시범을 쓰지 않는다")
    void notConfigured() {
        ExerciseResponse lunge = exerciseService.get("LUNGE");

        assertThat(lunge.guideStatus()).isEqualTo(ExerciseResponse.GuideStatus.NONE);
        assertThat(lunge.guideMotionRef()).isNull();
    }
}
