package com.team20.fitmate.domain.workout.service;

import com.team20.fitmate.domain.participant.entity.Participant;
import com.team20.fitmate.domain.participant.service.ParticipantService;
import com.team20.fitmate.domain.routine.entity.Routine;
import com.team20.fitmate.domain.routine.entity.RoutineExercise;
import com.team20.fitmate.domain.routine.entity.RoutineStatus;
import com.team20.fitmate.domain.routine.service.RoutineService;
import com.team20.fitmate.domain.workout.dto.ExerciseRecordRequest;
import com.team20.fitmate.domain.workout.dto.WorkoutFinishRequest;
import com.team20.fitmate.domain.workout.dto.WorkoutResponse;
import com.team20.fitmate.domain.workout.dto.WorkoutResultResponse;
import com.team20.fitmate.domain.workout.entity.ExerciseRecord;
import com.team20.fitmate.domain.workout.entity.Workout;
import com.team20.fitmate.domain.workout.entity.WorkoutStatus;
import com.team20.fitmate.domain.workout.repository.ExerciseRecordRepository;
import com.team20.fitmate.domain.workout.repository.WorkoutRepository;
import com.team20.fitmate.domain.xp.service.TeamXpService;
import com.team20.fitmate.global.exception.CustomException;
import com.team20.fitmate.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 운동 기록의 시작, 종료(수행 기록 확정과 팀 경험치 적립), 결과 조회를 맡는 서비스.
 * <p>
 * 운동 기록은 참가자 본인의 요청으로만 바뀐다. 다른 참가자의 종료·퇴장·연결 끊김은 이 기록에 영향을 주지 않는다.
 */
@Service
@RequiredArgsConstructor
public class WorkoutService {

    private final ParticipantService participantService;
    private final RoutineService routineService;
    private final WorkoutRepository workoutRepository;
    private final ExerciseRecordRepository exerciseRecordRepository;
    private final TeamXpService teamXpService;

    /**
     * 루틴으로 운동을 시작하고 운동 기록 식별값을 발급한다. 운동방은 참가자가 지금 입장해 있는 방으로 기록한다.
     * <p>
     * 같은 루틴으로 진행 중인 운동 기록이 있으면 새로 만들지 않고 그 기록을 돌려준다.
     * 연결이 끊겼다가 다시 시작 요청을 보내도 같은 기록으로 이어 가기 위해서다.
     *
     * @throws CustomException 루틴이 없거나 본인 것이 아니면 {@link ErrorCode#ROUTINE_NOT_FOUND},
     *                         추천 완료 루틴이 아니면 {@link ErrorCode#ROUTINE_NOT_STARTABLE}
     */
    @Transactional
    public WorkoutResponse start(String clientToken, Long routineId) {
        Participant participant = participantService.findByClientToken(clientToken);
        Routine routine = routineService.findOwnedRoutine(participant, routineId);
        if (routine.getStatus() != RoutineStatus.COMPLETED) {
            throw new CustomException(ErrorCode.ROUTINE_NOT_STARTABLE);
        }

        Workout workout = workoutRepository
                .findFirstByParticipantAndRoutineAndStatusOrderByIdDesc(participant, routine, WorkoutStatus.IN_PROGRESS)
                .orElseGet(() -> workoutRepository.save(
                        Workout.start(participant, routine, participant.getRoom(), LocalDateTime.now())));
        return WorkoutResponse.from(workout, List.of());
    }

    /**
     * 운동을 종료하고 운동별 수행 기록을 확정 저장한 뒤, 같은 트랜잭션에서 팀 경험치를 적립한다.
     * 확정한 뒤에는 다시 제출할 수 없다.
     * <p>
     * 루틴의 운동 중 제출되지 않은 운동은 "측정 못 함"(값 모두 null)으로 저장해, 0회로 오해되지 않게 한다.
     * 종료 요청이 동시에 두 번 와도 행 잠금으로 한 번만 확정된다.
     *
     * @throws CustomException 운동 기록이 없거나 본인 것이 아니면 {@link ErrorCode#WORKOUT_NOT_FOUND},
     *                         이미 종료됐으면 {@link ErrorCode#WORKOUT_ALREADY_FINISHED},
     *                         루틴에 없는 운동이거나 같은 운동을 두 번 보내거나 값이 규칙에 맞지 않으면
     *                         {@link ErrorCode#INVALID_EXERCISE_RECORD}
     */
    @Transactional
    public WorkoutResponse finish(String clientToken, Long workoutId, WorkoutFinishRequest request) {
        Participant participant = participantService.findByClientToken(clientToken);
        Workout workout = workoutRepository.findByIdForUpdate(workoutId)
                .filter(found -> found.isOwnedBy(participant))
                .orElseThrow(() -> new CustomException(ErrorCode.WORKOUT_NOT_FOUND));
        if (workout.isFinished()) {
            throw new CustomException(ErrorCode.WORKOUT_ALREADY_FINISHED);
        }

        List<ExerciseRecord> records = exerciseRecordRepository.saveAll(confirmRecords(workout, request.records()));
        workout.finish(LocalDateTime.now());
        teamXpService.accrue(workout, records);
        return WorkoutResponse.from(workout, records);
    }

    /**
     * 본인 운동 기록의 결과(확정된 수행 기록과 팀 경험치 변화)를 조회한다. 진행 중이면 수행 기록은 빈 목록이다.
     *
     * @throws CustomException 운동 기록이 없거나 본인 것이 아니면 {@link ErrorCode#WORKOUT_NOT_FOUND}
     */
    @Transactional(readOnly = true)
    public WorkoutResultResponse getResult(String clientToken, Long workoutId) {
        Participant participant = participantService.findByClientToken(clientToken);
        Workout workout = workoutRepository.findById(workoutId)
                .filter(found -> found.isOwnedBy(participant))
                .orElseThrow(() -> new CustomException(ErrorCode.WORKOUT_NOT_FOUND));
        List<ExerciseRecord> records = exerciseRecordRepository.findByWorkoutIdOrderByIdAsc(workoutId);
        return new WorkoutResultResponse(WorkoutResponse.from(workout, records), teamXpService.getChange(workout));
    }

    /**
     * 제출된 수행 기록을 루틴 순서대로 엔티티로 만든다. 제출되지 않은 운동은 측정 못 함으로 채운다.
     */
    private List<ExerciseRecord> confirmRecords(Workout workout, List<ExerciseRecordRequest> submitted) {
        List<RoutineExercise> routineExercises = workout.getRoutine() == null
                ? List.of()
                : workout.getRoutine().getRoutineExercises();
        Map<Long, RoutineExercise> routineExercisesById = routineExercises.stream()
                .collect(Collectors.toMap(RoutineExercise::getId, Function.identity()));

        Map<Long, ExerciseRecordRequest> submittedById = new HashMap<>();
        for (ExerciseRecordRequest record : submitted) {
            boolean inRoutine = routineExercisesById.containsKey(record.routineExerciseId());
            boolean duplicated = submittedById.putIfAbsent(record.routineExerciseId(), record) != null;
            if (!inRoutine || duplicated) {
                throw new CustomException(ErrorCode.INVALID_EXERCISE_RECORD);
            }
        }

        List<ExerciseRecord> records = new ArrayList<>();
        for (RoutineExercise routineExercise : routineExercises) {
            ExerciseRecordRequest record = submittedById.get(routineExercise.getId());
            ExerciseRecord.Values values = record == null ? ExerciseRecord.Values.notMeasured() : record.toValues();
            records.add(ExerciseRecord.of(workout, routineExercise, values));
        }
        return records;
    }
}
