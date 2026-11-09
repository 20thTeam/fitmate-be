package com.team20.fitmate.domain.workout.repository;

import com.team20.fitmate.domain.participant.entity.Participant;
import com.team20.fitmate.domain.routine.entity.Routine;
import com.team20.fitmate.domain.workout.entity.Workout;
import com.team20.fitmate.domain.workout.entity.WorkoutStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

/**
 * 운동 기록 저장소.
 */
public interface WorkoutRepository extends JpaRepository<Workout, Long> {

    /**
     * 참가자가 같은 루틴으로 진행 중인 운동 기록을 찾는다. 재접속 후 시작 요청이 다시 와도 같은 기록을 이어 쓰기 위해 쓴다.
     */
    Optional<Workout> findFirstByParticipantAndRoutineAndStatusOrderByIdDesc(
            Participant participant, Routine routine, WorkoutStatus status);

    /**
     * 종료 처리용으로 운동 기록을 행 잠금과 함께 조회한다. 종료 요청이 동시에 두 번 와도 한 번만 확정되게 한다.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select w from Workout w where w.id = :workoutId")
    Optional<Workout> findByIdForUpdate(@Param("workoutId") Long workoutId);
}
