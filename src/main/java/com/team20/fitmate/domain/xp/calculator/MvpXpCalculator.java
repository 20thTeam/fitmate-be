package com.team20.fitmate.domain.xp.calculator;

import com.team20.fitmate.domain.exercise.entity.MeasureType;
import com.team20.fitmate.domain.routine.entity.RoutineExercise;
import com.team20.fitmate.domain.workout.entity.ExerciseRecord;
import com.team20.fitmate.domain.workout.entity.MeasurementStatus;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 해커톤 MVP 경험치 산식 (PM 확정, 2026-11-09).
 * <ul>
 *     <li>횟수형: 유효 횟수 1회당 1XP. 추천 목표 횟수(세트당 횟수 × 세트 수)까지만 준다.</li>
 *     <li>시간형: 목표 시간을 채운 세트 1개당 5XP. 달성 세트 수는 유효 유지 시간 ÷ 목표 시간으로 세고,
 *     추천 세트 수까지만 준다.</li>
 *     <li>루틴의 모든 운동이 목표를 채우면 20XP 보너스.</li>
 *     <li>측정 못 한 운동은 0XP이고 목표 미달로 본다.</li>
 * </ul>
 * 하루 최대 경험치는 참가자별 누적이 필요해 {@code TeamXpService}에서 적용한다.
 * <p>
 * 횟수 상한의 세트 합계 해석, 시간형 달성 세트 계산, 완료 보너스 조건은 PM 확인 중인 해석이다.
 */
@Component
public class MvpXpCalculator implements XpCalculator {

    /** 횟수형 유효 횟수 1회당 경험치 */
    static final int XP_PER_VALID_COUNT = 1;

    /** 시간형 목표 시간을 채운 세트 1개당 경험치 */
    static final int XP_PER_TIME_SET = 5;

    /** 루틴 전체 목표 달성 보너스 */
    static final int ROUTINE_COMPLETE_BONUS = 20;

    @Override
    public boolean isDecided() {
        return true;
    }

    @Override
    public int calculate(List<ExerciseRecord> records) {
        int xp = 0;
        boolean allAchieved = !records.isEmpty();
        for (ExerciseRecord record : records) {
            Result result = score(record);
            xp += result.xp();
            allAchieved &= result.achieved();
        }
        return allAchieved ? xp + ROUTINE_COMPLETE_BONUS : xp;
    }

    /**
     * 운동 하나의 경험치와 목표 달성 여부를 계산한다. 루틴 운동이 아니거나 측정 못 했으면 0XP, 미달성이다.
     */
    private Result score(ExerciseRecord record) {
        RoutineExercise target = record.getRoutineExercise();
        if (target == null || record.getMeasurementStatus() != MeasurementStatus.MEASURED) {
            return new Result(0, false);
        }

        if (record.getExercise().getMeasureType() == MeasureType.COUNT) {
            int targetTotal = target.getTargetCount() * target.getTargetSets();
            int valid = record.getValidCount();
            return new Result(Math.min(valid, targetTotal) * XP_PER_VALID_COUNT, valid >= targetTotal);
        }

        int achievedSets = Math.min(record.getActualSeconds() / target.getTargetSeconds(), target.getTargetSets());
        return new Result(achievedSets * XP_PER_TIME_SET, achievedSets >= target.getTargetSets());
    }

    /** 운동 하나의 경험치와 목표 달성 여부 */
    private record Result(int xp, boolean achieved) {
    }
}
