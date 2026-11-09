package com.team20.fitmate.domain.workout.repository;

import com.team20.fitmate.domain.workout.entity.Workout;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 운동 기록 저장소.
 */
public interface WorkoutRepository extends JpaRepository<Workout, Long> {
}
