package com.team20.fitmate.domain.routine.recommend;

import com.team20.fitmate.domain.routine.entity.AdjustmentType;
import com.team20.fitmate.domain.routine.entity.RoutineExercise;
import com.team20.fitmate.domain.workout.entity.ExerciseRecord;
import com.team20.fitmate.domain.workout.entity.MeasurementStatus;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 시연용 임시 다음 회차 규칙. 프론트 임시 규칙({@code fitmate-fe src/lib/recommend.ts})과 같은 값을 쓴다.
 * <p>
 * 기능명세서 8.2에 따라 AI·PM 기준이 정해지기 전에 시연용 임시 기준을 먼저 적용한다. 값이 정해지면 이 클래스만 바꾼다.
 * <ul>
 *     <li>운동마다 목표 대비 유효 수행 비율 = 유효 횟수(시간형은 유지 시간) ÷ (목표 × 세트)</li>
 *     <li>90% 이상이면 상향: 횟수형은 15% 올림(최대 30회), 시간형은 10초 늘림(최대 60초)</li>
 *     <li>90% 미만이거나 안전 상한이면 유지</li>
 *     <li>기록이 없거나 측정하지 못한 운동은 근거가 없으므로 유지</li>
 * </ul>
 * 전체 조정 유형은 하나라도 올렸으면 상향, 모든 운동에 근거가 없으면 분석 불가, 나머지는 유지다.
 */
@Component
public class ProvisionalNextRoundRecommender implements NextRoundRecommender {

    /** '잘했다' 판정 기준 (%). 목표 대비 유효 비율이 이 값 이상이면 상향 후보 */
    static final int GOOD_PERCENT = 90;

    /** 횟수형 증가율 (%) */
    static final int COUNT_INCREASE_PERCENT = 15;

    /** 시간형 증가 폭 (초) */
    static final int SECONDS_STEP = 10;

    /** 횟수형 안전 상한 (회) */
    static final int MAX_COUNT = 30;

    /** 시간형 안전 상한 (초) */
    static final int MAX_SECONDS = 60;

    @Override
    public NextRoundPlan plan(List<ExerciseResult> results) {
        List<NextRoundPlan.AdjustedExercise> exercises = new ArrayList<>();
        boolean raised = false;
        boolean anyEvidence = false;
        for (ExerciseResult result : results) {
            RoutineExercise target = result.target();
            String name = target.getExercise().getName();
            int current = currentAmount(target);
            Double ratio = achievedRatio(result);
            if (ratio == null) {
                exercises.add(new NextRoundPlan.AdjustedExercise(current, name + ": 기록이 부족해 같은 난도를 유지해요."));
                continue;
            }
            anyEvidence = true;
            long percent = Math.round(ratio * 100);
            if (ratio * 100 < GOOD_PERCENT) {
                exercises.add(new NextRoundPlan.AdjustedExercise(current,
                        name + ": 목표의 " + percent + "%를 수행해 같은 난도를 유지해요."));
                continue;
            }
            int next = increased(target);
            if (next <= current) {
                exercises.add(new NextRoundPlan.AdjustedExercise(current, name + ": 안전 상한에 도달해 같은 난도를 유지해요."));
                continue;
            }
            raised = true;
            String unit = isCount(target) ? "회" : "초";
            exercises.add(new NextRoundPlan.AdjustedExercise(next, name + ": 목표의 " + percent + "%를 수행해 "
                    + current + unit + " → " + next + unit + "로 늘렸어요."));
        }
        AdjustmentType type = raised ? AdjustmentType.UP
                : anyEvidence ? AdjustmentType.KEEP : AdjustmentType.UNAVAILABLE;
        return new NextRoundPlan(type, exercises);
    }

    /** 횟수형인지 본다. 목표 횟수가 있으면 횟수형이다 */
    private static boolean isCount(RoutineExercise target) {
        return target.getTargetCount() != null;
    }

    /** 이전 회차 목표 수행량 */
    private static int currentAmount(RoutineExercise target) {
        return isCount(target) ? target.getTargetCount() : target.getTargetSeconds();
    }

    /**
     * 목표 대비 유효 수행 비율. 근거가 없으면 null.
     * 판정은 반올림 전 비율로 한다(89.6%는 상향하지 않는다). 사유 문구의 %만 반올림한다.
     */
    private static Double achievedRatio(ExerciseResult result) {
        ExerciseRecord record = result.record();
        if (record == null || record.getMeasurementStatus() != MeasurementStatus.MEASURED) {
            return null;
        }
        RoutineExercise target = result.target();
        Integer achieved = isCount(target) ? record.getValidCount() : record.getActualSeconds();
        int goal = currentAmount(target) * target.getTargetSets();
        if (achieved == null || goal <= 0) {
            return null;
        }
        return (double) achieved / goal;
    }

    /** 안전 상한 안에서 올린 수행량 */
    private static int increased(RoutineExercise target) {
        if (isCount(target)) {
            int count = target.getTargetCount();
            int raised = (count * (100 + COUNT_INCREASE_PERCENT) + 99) / 100;
            return Math.min(MAX_COUNT, raised);
        }
        return Math.min(MAX_SECONDS, target.getTargetSeconds() + SECONDS_STEP);
    }
}
