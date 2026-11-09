package com.team20.fitmate.domain.workout.repository;

import com.team20.fitmate.domain.workout.entity.ExerciseRecord;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * 수행 기록 저장소.
 */
public interface ExerciseRecordRepository extends JpaRepository<ExerciseRecord, Long> {

    /**
     * 운동 기록에 속한 수행 기록을 저장 순서대로 찾는다.
     */
    List<ExerciseRecord> findByWorkoutIdOrderByIdAsc(Long workoutId);
}
