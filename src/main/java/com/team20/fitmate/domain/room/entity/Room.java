package com.team20.fitmate.domain.room.entity;

import com.team20.fitmate.global.common.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 운동방. 참가자들이 함께 접속하는 3D 공간이다. 시연에서는 미리 지정된 방 하나로 입장한다.
 * <p>
 * {@code capacity}는 동시에 접속할 수 있는 인원이다. 참가자가 DB에서 지워지지 않으므로
 * 정원 초과 판단은 DB가 아니라 실시간 계층에서 현재 연결 수로 한다.
 */
@Getter
@Entity
@Table(name = "room")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Room extends BaseTimeEntity {

    /** 운동방 식별값 */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 이 운동방을 운영하는 팀 */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "team_id", nullable = false)
    private Team team;

    /** 운동방 이름 */
    @Column(name = "name", nullable = false, length = 30)
    private String name;

    /** 정원 (동시 접속 인원) */
    @Column(name = "capacity", nullable = false)
    private int capacity;

    private Room(Team team, String name, int capacity) {
        this.team = team;
        this.name = name;
        this.capacity = capacity;
    }

    /**
     * 새 운동방을 만든다.
     */
    public static Room create(Team team, String name, int capacity) {
        return new Room(team, name, capacity);
    }
}
