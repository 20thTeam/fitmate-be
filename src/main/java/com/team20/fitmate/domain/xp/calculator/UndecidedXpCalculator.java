package com.team20.fitmate.domain.xp.calculator;

import com.team20.fitmate.domain.workout.entity.ExerciseRecord;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 경험치 산식이 정해지기 전의 기본 구현. 임의 수치를 만들지 않도록 "미정"만 알린다.
 * <p>
 * PM이 산식을 정하면 이 클래스를 그 산식의 구현체로 바꾼다.
 */
@Component
public class UndecidedXpCalculator implements XpCalculator {

    @Override
    public boolean isDecided() {
        return false;
    }

    @Override
    public int calculate(List<ExerciseRecord> records) {
        throw new IllegalStateException("경험치 산식이 정해지지 않았습니다.");
    }
}
