package com.team20.fitmate.domain.participant.repository;

import com.team20.fitmate.domain.participant.entity.Participant;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * 참가자 저장소.
 */
public interface ParticipantRepository extends JpaRepository<Participant, Long> {

    /**
     * 기기에 보관된 재방문 토큰으로 참가자를 찾는다.
     */
    Optional<Participant> findByClientToken(String clientToken);
}
