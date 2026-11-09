package com.team20.fitmate.domain.room.repository;

import com.team20.fitmate.domain.room.entity.Room;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * 운동방 저장소.
 */
public interface RoomRepository extends JpaRepository<Room, Long> {

    /**
     * 모든 운동방을 팀과 함께 식별값 순서로 조회한다.
     */
    @EntityGraph(attributePaths = "team")
    List<Room> findAllByOrderByIdAsc();
}
