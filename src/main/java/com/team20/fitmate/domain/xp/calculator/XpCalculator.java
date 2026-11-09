package com.team20.fitmate.domain.xp.calculator;

import com.team20.fitmate.domain.workout.entity.ExerciseRecord;

import java.util.List;

/**
 * 확정된 수행 기록으로 팀 경험치를 계산하는 규칙.
 * <p>
 * 산식은 PM이 정한다(이슈 #8, 기능명세서 9.1 "확정 전까지 임의의 수치로 정하지 않는다").
 * 지금 구현은 PM이 확정한 해커톤 MVP 산식({@link MvpXpCalculator})이다.
 * 산식이 바뀌면 구현체만 바꾸고, 적립·조회 흐름은 그대로 둔다.
 */
public interface XpCalculator {

    /**
     * 산식이 정해졌는지. 정해지지 않았으면 적립하지 않고, 경험치를 실제 값으로 내려주지 않는다.
     */
    boolean isDecided();

    /**
     * 운동 기록 하나의 확정된 수행 기록으로 적립 경험치를 계산한다. {@link #isDecided()}가 참일 때만 부른다.
     *
     * @param records 운동별 수행 기록 (측정 못 함 포함)
     * @return 0 이상의 적립 경험치
     */
    int calculate(List<ExerciseRecord> records);
}
