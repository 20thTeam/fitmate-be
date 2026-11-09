package com.team20.fitmate.domain.xp.repository;

import com.team20.fitmate.domain.xp.entity.TeamXpLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * 팀 경험치 적립 기록 저장소.
 */
public interface TeamXpLogRepository extends JpaRepository<TeamXpLog, Long> {

    /** 운동 기록에 대한 적립 기록을 찾는다 */
    Optional<TeamXpLog> findByWorkoutId(Long workoutId);

    /** 운동 기록으로 이미 적립했는지 확인한다 */
    boolean existsByWorkoutId(Long workoutId);
}
