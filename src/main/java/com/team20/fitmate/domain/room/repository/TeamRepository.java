package com.team20.fitmate.domain.room.repository;

import com.team20.fitmate.domain.room.entity.Team;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * 팀 저장소.
 */
public interface TeamRepository extends JpaRepository<Team, Long> {

    /**
     * 팀 누적 경험치를 DB에서 바로 더한다. 팀원 여러 명이 동시에 운동을 끝내도 적립이 빠지지 않게 한다.
     */
    @Modifying(flushAutomatically = true)
    @Query("update Team t set t.totalXp = t.totalXp + :xp where t.id = :teamId")
    int addXp(@Param("teamId") Long teamId, @Param("xp") int xp);

    /**
     * 팀 누적 경험치를 DB에서 바로 읽는다. 같은 트랜잭션에서 {@link #addXp}한 뒤에도 최신 값을 얻기 위해 쓴다.
     */
    @Query("select t.totalXp from Team t where t.id = :teamId")
    int findTotalXp(@Param("teamId") Long teamId);
}
