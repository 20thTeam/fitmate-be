package com.team20.fitmate.domain.routine.repository;

import com.team20.fitmate.domain.routine.entity.Routine;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * 루틴 저장소. 구성 운동은 루틴과 함께 저장된다.
 */
public interface RoutineRepository extends JpaRepository<Routine, Long> {

    /**
     * 운동 기록을 근거로 이미 만든 다음 회차 루틴을 찾는다.
     */
    Optional<Routine> findByBasedOnWorkoutId(Long workoutId);

    /**
     * 여러 운동 기록을 근거로 만든 다음 회차 루틴을 한 번에 찾는다(성장 기록용).
     */
    List<Routine> findByBasedOnWorkoutIdIn(Collection<Long> workoutIds);
}
