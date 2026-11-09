package com.team20.fitmate.domain.room.repository;

import com.team20.fitmate.domain.room.entity.Team;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 팀 저장소.
 */
public interface TeamRepository extends JpaRepository<Team, Long> {
}
