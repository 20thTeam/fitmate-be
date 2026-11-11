package com.team20.fitmate.domain.workout.service;

import com.team20.fitmate.domain.exercise.entity.Exercise;
import com.team20.fitmate.domain.exercise.repository.ExerciseRepository;
import com.team20.fitmate.domain.participant.entity.Participant;
import com.team20.fitmate.domain.participant.service.ParticipantService;
import com.team20.fitmate.domain.workout.dto.PublicPartyFinishRequest;
import com.team20.fitmate.domain.workout.dto.WorkoutResponse;
import com.team20.fitmate.domain.workout.entity.ExerciseRecord;
import com.team20.fitmate.domain.workout.entity.Workout;
import com.team20.fitmate.domain.workout.entity.WorkoutStatus;
import com.team20.fitmate.domain.workout.repository.ExerciseRecordRepository;
import com.team20.fitmate.domain.workout.repository.WorkoutRepository;
import com.team20.fitmate.global.exception.CustomException;
import com.team20.fitmate.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 공개 파티(선셋 에어로빅) 운동 기록의 시작과 종료를 맡는 서비스 (이슈 #11).
 * <p>
 * 루틴 운동 기록(S7)과 같은 테이블을 쓰되 루틴·운동방 없이 저장한다. 친구 팀이 없으므로 팀 경험치는 쌓지 않고,
 * 루틴이 없으므로 2회차 추천도 만들지 않는다. 다른 참가자의 시작·종료와 무관하게 본인 요청으로만 바뀐다.
 */
@Service
@RequiredArgsConstructor
public class PublicPartyWorkoutService {

    /** 공개 파티 동작. 프론트 선셋 화면과 같은 점핑잭이다. */
    public static final String EXERCISE_CODE = "JUMPING_JACK";

    private final ParticipantService participantService;
    private final ExerciseRepository exerciseRepository;
    private final WorkoutRepository workoutRepository;
    private final ExerciseRecordRepository exerciseRecordRepository;

    /**
     * 공개 파티 운동을 시작한다. 진행 중인 공개 파티 기록이 있으면 새로 만들지 않고 그 기록을 돌려준다(재접속 시 이어 하기).
     */
    @Transactional
    public WorkoutResponse start(String clientToken) {
        Participant participant = participantService.findByClientToken(clientToken);
        Workout workout = workoutRepository
                .findFirstByParticipantAndRoutineIsNullAndStatusOrderByIdDesc(participant, WorkoutStatus.IN_PROGRESS)
                .orElseGet(() -> workoutRepository.save(Workout.startPublicParty(participant, LocalDateTime.now())));
        return WorkoutResponse.from(workout, List.of());
    }

    /**
     * 공개 파티 운동을 종료하고 본인 수행 기록(점핑잭 하나)을 확정 저장한다. 확정한 뒤에는 다시 제출할 수 없다.
     * 종료 요청이 동시에 두 번 와도 행 잠금으로 한 번만 확정된다.
     *
     * @throws CustomException 공개 파티 기록이 없거나 본인 것이 아니면 {@link ErrorCode#WORKOUT_NOT_FOUND},
     *                         이미 종료됐으면 {@link ErrorCode#WORKOUT_ALREADY_FINISHED},
     *                         값이 측정 상태 규칙에 맞지 않으면 {@link ErrorCode#INVALID_EXERCISE_RECORD}
     */
    @Transactional
    public WorkoutResponse finish(String clientToken, Long workoutId, PublicPartyFinishRequest request) {
        Participant participant = participantService.findByClientToken(clientToken);
        Workout workout = workoutRepository.findByIdForUpdate(workoutId)
                .filter(found -> found.isOwnedBy(participant) && found.isPublicParty())
                .orElseThrow(() -> new CustomException(ErrorCode.WORKOUT_NOT_FOUND));
        if (workout.isFinished()) {
            throw new CustomException(ErrorCode.WORKOUT_ALREADY_FINISHED);
        }

        Exercise exercise = exerciseRepository.findByCode(EXERCISE_CODE)
                .orElseThrow(() -> new CustomException(ErrorCode.EXERCISE_NOT_FOUND));
        ExerciseRecord record = exerciseRecordRepository.save(
                ExerciseRecord.withoutRoutine(workout, exercise, request.toValues()));
        workout.finish(LocalDateTime.now());
        return WorkoutResponse.from(workout, List.of(record));
    }
}
