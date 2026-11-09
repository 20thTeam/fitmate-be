package com.team20.fitmate.domain.room.entity;

import com.team20.fitmate.global.common.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 팀. 운동 성취가 함께 쌓이는 단위다.
 * <p>
 * 시연에서는 운동방 하나에 팀 하나로 임시 가정한다(확인 필요).
 * 경험치 적립은 팀 경험치 슬라이스(S8)에서 추가한다.
 */
@Getter
@Entity
@Table(name = "team")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Team extends BaseTimeEntity {

    /** 팀 식별값 */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 팀 이름 */
    @Column(name = "name", nullable = false, length = 30)
    private String name;

    /** 누적 경험치. 처음에는 0이다 */
    @Column(name = "total_xp", nullable = false)
    private int totalXp;

    private Team(String name) {
        this.name = name;
        this.totalXp = 0;
    }

    /**
     * 경험치 0인 새 팀을 만든다.
     */
    public static Team create(String name) {
        return new Team(name);
    }
}
