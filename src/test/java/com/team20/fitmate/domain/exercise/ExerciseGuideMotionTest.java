package com.team20.fitmate.domain.exercise;

import com.team20.fitmate.domain.exercise.entity.Exercise;
import com.team20.fitmate.domain.exercise.entity.MeasureType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 운동의 가이드 시범 참조 변경 규칙을 확인하는 단위 테스트.
 */
class ExerciseGuideMotionTest {

    @Test
    @DisplayName("빈 값이나 공백이면 시범 없음(null)으로 두고, 앞뒤 공백은 지운다")
    void changeGuideMotionRef() {
        Exercise squat = Exercise.create("SQUAT", "스쿼트", MeasureType.COUNT, true);
        assertThat(squat.hasGuideMotion()).isFalse();

        squat.changeGuideMotionRef("  guide/squat.glb ");
        assertThat(squat.getGuideMotionRef()).isEqualTo("guide/squat.glb");
        assertThat(squat.hasGuideMotion()).isTrue();

        squat.changeGuideMotionRef(" ");
        assertThat(squat.getGuideMotionRef()).isNull();
        squat.changeGuideMotionRef(null);
        assertThat(squat.hasGuideMotion()).isFalse();
    }
}
