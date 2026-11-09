package com.team20.fitmate.domain.routine.repository;

import com.team20.fitmate.domain.routine.entity.Routine;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 루틴 저장소. 구성 운동은 루틴과 함께 저장된다.
 */
public interface RoutineRepository extends JpaRepository<Routine, Long> {
}
