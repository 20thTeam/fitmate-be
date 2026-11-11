package com.team20.fitmate.domain.cheer.repository;

import com.team20.fitmate.domain.cheer.entity.Cheer;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * 응원 기록 저장소.
 */
public interface CheerRepository extends JpaRepository<Cheer, Long> {

    /**
     * 참가자가 받은 응원을 오래된 순서로 찾는다.
     */
    List<Cheer> findByReceiverIdOrderByIdAsc(Long receiverId);
}
