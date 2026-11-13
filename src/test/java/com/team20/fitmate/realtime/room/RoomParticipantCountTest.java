package com.team20.fitmate.realtime.room;

import com.team20.fitmate.domain.room.dto.RoomResponse;
import com.team20.fitmate.domain.room.service.RoomService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 운동방 조회 응답의 participantCount가 실시간 레지스트리의 방별 인원(정원 판단과 같은 기준)과 맞는지 확인한다 (이슈 #36).
 */
@SpringBootTest
class RoomParticipantCountTest {

    @Autowired
    private RoomSessionRegistry registry;

    @Autowired
    private RoomService roomService;

    private RoomResponse first;
    private RoomResponse second;

    @BeforeEach
    void setUp() {
        registry.clear();
        List<RoomResponse> rooms = roomService.getAll();
        first = rooms.get(0);
        second = rooms.get(1);
    }

    @AfterEach
    void tearDown() {
        registry.clear();
    }

    /** 연결을 만들고 운동방에 JOIN한다 */
    private FakeWebSocketSession join(String sessionId, RoomResponse room, long participantId) {
        FakeWebSocketSession session = new FakeWebSocketSession(sessionId);
        registry.connect(session);
        registry.join(sessionId, new JoinTicket(room.roomId(), room.capacity(), participantId, "참가자" + participantId, "CHARACTER_01"));
        return session;
    }

    @Test
    @DisplayName("아무도 없으면 0명이다")
    void emptyRoom() {
        assertThat(roomService.get(first.roomId()).participantCount()).isZero();
        assertThat(roomService.getAll()).allSatisfy(room -> assertThat(room.participantCount()).isZero());
    }

    @Test
    @DisplayName("방마다 지금 들어와 있는 인원을 따로 센다")
    void countsPerRoom() {
        join("s1", first, 1L);
        join("s2", first, 2L);
        join("s3", second, 3L);

        assertThat(roomService.get(first.roomId()).participantCount()).isEqualTo(2);
        assertThat(roomService.get(second.roomId()).participantCount()).isEqualTo(1);
        assertThat(roomService.getAll().get(0).participantCount()).isEqualTo(2);
    }

    @Test
    @DisplayName("재접속을 기다리는 참가자도 세고, 나가면 빠진다")
    void countsDisconnectedAndLeave() {
        join("s1", first, 1L);
        join("s2", first, 2L);

        registry.disconnected("s1");
        assertThat(roomService.get(first.roomId()).participantCount()).isEqualTo(2);

        registry.leave("s2");
        assertThat(roomService.get(first.roomId()).participantCount()).isEqualTo(1);
    }
}
