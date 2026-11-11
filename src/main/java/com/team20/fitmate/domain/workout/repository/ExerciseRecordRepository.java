package com.team20.fitmate.domain.workout.repository;

import com.team20.fitmate.domain.workout.entity.ExerciseRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

/**
 * 수행 기록 저장소.
 */
public interface ExerciseRecordRepository extends JpaRepository<ExerciseRecord, Long> {

    /**
     * 운동 기록에 속한 수행 기록을 저장 순서대로 찾는다.
     */
    List<ExerciseRecord> findByWorkoutIdOrderByIdAsc(Long workoutId);

    /**
     * 여러 운동 기록의 수행 기록을 운동 정보·루틴 운동과 함께 한 번에 찾는다(성장 기록용).
     */
    @Query("select r from ExerciseRecord r join fetch r.exercise left join fetch r.routineExercise "
            + "where r.workout.id in :workoutIds order by r.id asc")
    List<ExerciseRecord> findByWorkoutIds(@Param("workoutIds") Collection<Long> workoutIds);
}
