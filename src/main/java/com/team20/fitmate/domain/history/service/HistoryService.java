package com.team20.fitmate.domain.history.service;

import com.team20.fitmate.domain.history.dto.HistoryResponse;
import com.team20.fitmate.domain.history.dto.HistorySummary;
import com.team20.fitmate.domain.history.dto.HistoryWorkoutResponse;
import com.team20.fitmate.domain.history.dto.NextRoutineSummary;
import com.team20.fitmate.domain.participant.entity.Participant;
import com.team20.fitmate.domain.participant.service.ParticipantService;
import com.team20.fitmate.domain.routine.entity.Routine;
import com.team20.fitmate.domain.routine.repository.RoutineRepository;
import com.team20.fitmate.domain.workout.dto.ExerciseRecordResponse;
import com.team20.fitmate.domain.workout.entity.ExerciseRecord;
import com.team20.fitmate.domain.workout.entity.MeasurementStatus;
import com.team20.fitmate.domain.workout.entity.Workout;
import com.team20.fitmate.domain.workout.entity.WorkoutStatus;
import com.team20.fitmate.domain.workout.repository.ExerciseRecordRepository;
import com.team20.fitmate.domain.workout.repository.WorkoutRepository;
import com.team20.fitmate.domain.xp.calculator.XpCalculator;
import com.team20.fitmate.domain.xp.entity.TeamXpLog;
import com.team20.fitmate.domain.xp.repository.TeamXpLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 내 성장 기록 조회(기능 8.3). 별도 테이블 없이 종료된 운동 기록과 수행 기록, 루틴, 경험치 적립 기록을 모아 보여준다.
 * <p>
 * 토큰의 참가자 본인 기록만 읽는다. 진행 중인 기록은 확정 전이라 넣지 않는다.
 * 예시 수치를 만들지 않으며, 근거가 없는 합계는 0이 아니라 null로 둔다.
 */
@Service
@RequiredArgsConstructor
public class HistoryService {

    private final ParticipantService participantService;
    private final WorkoutRepository workoutRepository;
    private final ExerciseRecordRepository exerciseRecordRepository;
    private final RoutineRepository routineRepository;
    private final TeamXpLogRepository teamXpLogRepository;
    private final XpCalculator xpCalculator;

    /**
     * 토큰의 참가자 본인 성장 기록을 조회한다. 조회는 회차 수와 관계없이 쿼리 5번으로 끝난다.
     */
    @Transactional(readOnly = true)
    public HistoryResponse getMyHistory(String clientToken) {
        Participant participant = participantService.findByClientToken(clientToken);
        List<Workout> workouts = workoutRepository.findHistory(participant.getId(), WorkoutStatus.FINISHED);
        if (workouts.isEmpty()) {
            return new HistoryResponse(new HistorySummary(0, 0, null, null, xpCalculator.isDecided() ? 0 : null),
                    List.of());
        }

        List<Long> workoutIds = workouts.stream().map(Workout::getId).toList();
        Map<Long, List<ExerciseRecord>> recordsByWorkout = exerciseRecordRepository.findByWorkoutIds(workoutIds)
                .stream()
                .collect(Collectors.groupingBy(record -> record.getWorkout().getId()));
        Map<Long, Routine> nextByWorkout = routineRepository.findByBasedOnWorkoutIdIn(workoutIds).stream()
                .collect(Collectors.toMap(routine -> routine.getBasedOnWorkout().getId(), Function.identity()));
        Map<Long, Integer> xpByWorkout = xpCalculator.isDecided()
                ? teamXpLogRepository.findByWorkoutIdIn(workoutIds).stream()
                        .collect(Collectors.toMap(log -> log.getWorkout().getId(), TeamXpLog::getEarnedXp))
                : Map.of();

        List<HistoryWorkoutResponse> items = workouts.stream()
                .map(workout -> toItem(workout,
                        sortByRoutineOrder(recordsByWorkout.getOrDefault(workout.getId(), List.of())),
                        nextByWorkout.get(workout.getId()),
                        xpByWorkout.get(workout.getId())))
                .toList();
        return new HistoryResponse(summarize(items, xpByWorkout), items);
    }

    /** 수행 기록을 루틴 순서대로 정렬한다 */
    private static List<ExerciseRecord> sortByRoutineOrder(List<ExerciseRecord> records) {
        return records.stream()
                .sorted(Comparator.comparingInt((ExerciseRecord record) -> record.getRoutineExercise() == null
                                ? Integer.MAX_VALUE : record.getRoutineExercise().getSortOrder())
                        .thenComparing(ExerciseRecord::getId))
                .toList();
    }

    /** 운동 기록 하나를 회차 항목으로 바꾼다 */
    private static HistoryWorkoutResponse toItem(Workout workout, List<ExerciseRecord> records,
                                                 Routine next, Integer earnedXp) {
        Routine routine = workout.getRoutine();
        return new HistoryWorkoutResponse(
                workout.getId(),
                routine.getId(),
                routine.getRoundNo(),
                routine.getGoal(),
                routine.getGoal().getDisplayName(),
                workout.getStartedAt(),
                workout.getFinishedAt(),
                Duration.between(workout.getStartedAt(), workout.getFinishedAt()).toSeconds(),
                sumOrNull(records.stream().map(ExerciseRecord::getActualSeconds).toList()),
                sumOrNull(records.stream()
                        .filter(record -> record.getMeasurementStatus() == MeasurementStatus.MEASURED)
                        .map(ExerciseRecord::getValidCount)
                        .toList()),
                routine.getAdjustmentType(),
                routine.getAdjustmentReasons(),
                earnedXp,
                records.stream().map(ExerciseRecordResponse::from).toList(),
                next == null ? null : NextRoutineSummary.from(next));
    }

    /** 회차 항목으로 누적량을 만든다 */
    private HistorySummary summarize(List<HistoryWorkoutResponse> items, Map<Long, Integer> xpByWorkout) {
        return new HistorySummary(
                items.size(),
                items.stream().mapToLong(HistoryWorkoutResponse::elapsedSeconds).sum(),
                sumOrNull(items.stream().map(HistoryWorkoutResponse::exerciseSeconds).toList()),
                sumOrNull(items.stream().map(HistoryWorkoutResponse::totalValidCount).toList()),
                xpCalculator.isDecided() ? xpByWorkout.values().stream().mapToInt(Integer::intValue).sum() : null);
    }

    /** null을 뺀 합. 값이 하나도 없으면 null(0으로 꾸미지 않는다) */
    private static Integer sumOrNull(List<Integer> values) {
        List<Integer> present = values.stream().filter(Objects::nonNull).toList();
        return present.isEmpty() ? null : present.stream().mapToInt(Integer::intValue).sum();
    }
}
