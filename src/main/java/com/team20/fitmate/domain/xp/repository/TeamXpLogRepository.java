package com.team20.fitmate.domain.xp.repository;

import com.team20.fitmate.domain.xp.entity.TeamXpLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * 팀 경험치 적립 기록 저장소.
 */
public interface TeamXpLogRepository extends JpaRepository<TeamXpLog, Long> {

    /** 운동 기록에 대한 적립 기록을 찾는다 */
    Optional<TeamXpLog> findByWorkoutId(Long workoutId);

    /** 운동 기록으로 이미 적립했는지 확인한다 */
    boolean existsByWorkoutId(Long workoutId);

    /**
     * 참가자가 기간 안에 적립한 경험치 합계. 하루 최대 경험치 적용에 쓴다.
     */
    @Query("select coalesce(sum(l.earnedXp), 0) from TeamXpLog l "
            + "where l.participant.id = :participantId and l.createdAt >= :from and l.createdAt < :to")
    int sumEarnedXpByParticipantBetween(@Param("participantId") Long participantId,
                                        @Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    /**
     * 여러 운동 기록의 경험치 적립 기록을 한 번에 찾는다(성장 기록용).
     */
    List<TeamXpLog> findByWorkoutIdIn(Collection<Long> workoutIds);
}
