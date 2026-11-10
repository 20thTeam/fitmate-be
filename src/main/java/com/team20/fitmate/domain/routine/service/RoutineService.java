package com.team20.fitmate.domain.routine.service;

import com.team20.fitmate.domain.exercise.entity.Exercise;
import com.team20.fitmate.domain.exercise.repository.ExerciseRepository;
import com.team20.fitmate.domain.participant.entity.Participant;
import com.team20.fitmate.domain.participant.service.ParticipantService;
import com.team20.fitmate.domain.routine.dto.RoutineResponse;
import com.team20.fitmate.domain.routine.entity.Goal;
import com.team20.fitmate.domain.routine.entity.Routine;
import com.team20.fitmate.domain.routine.entity.RoutineExercise;
import com.team20.fitmate.domain.routine.recommend.RecommendedExercise;
import com.team20.fitmate.domain.routine.recommend.RoutineRecommender;
import com.team20.fitmate.domain.routine.repository.RoutineRepository;
import com.team20.fitmate.global.exception.CustomException;
import com.team20.fitmate.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 루틴 추천·저장과 조회를 맡는 서비스.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RoutineService {

    /** 첫 회차 번호 */
    private static final int FIRST_ROUND = 1;

    /** 루틴 하나에 담을 수 있는 운동 수 범위 */
    private static final int MIN_EXERCISES = 2;
    private static final int MAX_EXERCISES = 3;

    private final ParticipantService participantService;
    private final RoutineRecommender routineRecommender;
    private final ExerciseRepository exerciseRepository;
    private final RoutineRepository routineRepository;

    /**
     * 목표로 첫 회차 루틴을 추천받아 참가자에 연결해 저장한다.
     * <p>
     * 추천 규칙이 고른 운동 중 하나라도 인식 검증된 운동으로 찾지 못하거나 운동 수가 2~3개가 아니면
     * 없는 운동을 보여주지 않도록 추천 불가 루틴으로 저장한다.
     */
    @Transactional
    public RoutineResponse createFirstRound(String clientToken, Goal goal) {
        Participant participant = participantService.findByClientToken(clientToken);
        Routine routine = recommend(participant, goal);
        routineRepository.save(routine);
        return RoutineResponse.from(routine);
    }

    /**
     * 본인 루틴을 조회한다. 다른 참가자의 루틴이면 존재 여부를 숨기고 찾을 수 없다고 응답한다.
     */
    @Transactional(readOnly = true)
    public RoutineResponse get(String clientToken, Long routineId) {
        Participant participant = participantService.findByClientToken(clientToken);
        return RoutineResponse.from(findOwnedRoutine(participant, routineId));
    }

    /**
     * 참가자 본인의 루틴 엔티티를 찾는다. 운동 시작 등 다른 서비스에서도 쓴다.
     *
     * @throws CustomException 없거나 다른 참가자의 루틴이면 {@link ErrorCode#ROUTINE_NOT_FOUND}
     */
    @Transactional(readOnly = true)
    public Routine findOwnedRoutine(Participant participant, Long routineId) {
        return routineRepository.findById(routineId)
                .filter(found -> found.isOwnedBy(participant))
                .orElseThrow(() -> new CustomException(ErrorCode.ROUTINE_NOT_FOUND));
    }

    /**
     * 추천 규칙의 결과를 실제 운동으로 바꿔 루틴을 만든다.
     */
    private Routine recommend(Participant participant, Goal goal) {
        List<RecommendedExercise> recommended = routineRecommender.recommendFirstRound(goal);
        if (recommended.size() < MIN_EXERCISES || recommended.size() > MAX_EXERCISES) {
            log.warn("추천 불가: 목표 {}의 추천 운동 수 {}", goal, recommended.size());
            return Routine.unavailable(participant, FIRST_ROUND, goal);
        }

        List<String> codes = recommended.stream().map(RecommendedExercise::exerciseCode).toList();
        Map<String, Exercise> exercisesByCode = exerciseRepository.findByCodeInAndRecognitionVerifiedTrue(codes)
                .stream()
                .collect(Collectors.toMap(Exercise::getCode, Function.identity()));
        if (!exercisesByCode.keySet().containsAll(codes)) {
            log.warn("추천 불가: 목표 {}의 추천 운동 중 인식 검증된 운동이 아닌 것이 있음 {}", goal, codes);
            return Routine.unavailable(participant, FIRST_ROUND, goal);
        }

        Routine routine = Routine.completed(participant, FIRST_ROUND, goal);
        for (int i = 0; i < recommended.size(); i++) {
            RecommendedExercise item = recommended.get(i);
            routine.addExercise(RoutineExercise.of(
                    exercisesByCode.get(item.exerciseCode()), i + 1, item.amount(), item.sets(), item.tag()));
        }
        return routine;
    }
}
