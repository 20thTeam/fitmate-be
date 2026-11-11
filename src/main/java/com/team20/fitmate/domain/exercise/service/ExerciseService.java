package com.team20.fitmate.domain.exercise.service;

import com.team20.fitmate.domain.exercise.dto.ExerciseResponse;
import com.team20.fitmate.domain.exercise.repository.ExerciseRepository;
import com.team20.fitmate.global.exception.CustomException;
import com.team20.fitmate.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 운동 조회 서비스.
 */
@Service
@RequiredArgsConstructor
public class ExerciseService {

    private final ExerciseRepository exerciseRepository;

    /**
     * 운동 코드로 운동 하나와 가이드 시범 참조를 조회한다.
     *
     * @throws CustomException 없는 운동 코드면 {@link ErrorCode#EXERCISE_NOT_FOUND}
     */
    @Transactional(readOnly = true)
    public ExerciseResponse get(String exerciseCode) {
        return exerciseRepository.findByCode(exerciseCode)
                .map(ExerciseResponse::from)
                .orElseThrow(() -> new CustomException(ErrorCode.EXERCISE_NOT_FOUND));
    }
}
