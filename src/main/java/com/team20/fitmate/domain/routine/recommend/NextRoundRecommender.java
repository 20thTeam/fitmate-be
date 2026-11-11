package com.team20.fitmate.domain.routine.recommend;

import com.team20.fitmate.domain.routine.entity.RoutineExercise;
import com.team20.fitmate.domain.workout.entity.ExerciseRecord;

import java.util.List;

/**
 * 다음 회차 추천 규칙(기능 8.2).
 * <p>
 * '잘했다' 판정 기준, 증가 폭, 안전 상한은 AI 담당이 설계해 PM이 승인한다. 규칙이 정해지면
 * 이 인터페이스의 구현만 바꾸고 API는 그대로 둔다. 같은 입력에는 같은 결과를 내야 한다.
 */
public interface NextRoundRecommender {

    /**
     * 이전 회차 운동별 목표와 실제 기록으로 다음 회차 수행량과 조정 사유를 정한다.
     *
     * @param results 이전 회차 운동 순서대로의 목표와 기록
     * @return 같은 순서의 다음 회차 수행량과 전체 조정 유형
     */
    NextRoundPlan plan(List<ExerciseResult> results);

    /**
     * 이전 회차 운동 하나의 목표와 실제 기록.
     *
     * @param target 이전 회차 루틴의 운동 (목표 수행량·세트)
     * @param record 그 운동의 수행 기록. 없으면 null
     */
    record ExerciseResult(RoutineExercise target, ExerciseRecord record) {
    }
}
