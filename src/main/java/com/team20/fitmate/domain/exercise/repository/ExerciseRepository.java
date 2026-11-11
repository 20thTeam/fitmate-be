package com.team20.fitmate.domain.exercise.repository;

import com.team20.fitmate.domain.exercise.entity.Exercise;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * 운동 저장소.
 */
public interface ExerciseRepository extends JpaRepository<Exercise, Long> {

    /**
     * 주어진 코드 중 인식 검증이 끝난 운동만 찾는다. 추천 규칙이 고른 운동을 실제 운동으로 바꿀 때 쓴다.
     */
    List<Exercise> findByCodeInAndRecognitionVerifiedTrue(Collection<String> codes);

    /**
     * 같은 코드의 운동이 이미 있는지 확인한다. 초기 데이터 중복 적재를 막는다.
     */
    boolean existsByCode(String code);

    /**
     * 코드로 운동을 찾는다.
     */
    Optional<Exercise> findByCode(String code);
}
