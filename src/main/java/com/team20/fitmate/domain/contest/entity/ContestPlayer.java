package com.team20.fitmate.domain.contest.entity;

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
 * 학교 대항 스쿼트 챌린지 판에 들어온 사람 한 명과 그 사람의 한 판 결과 (이슈 #34).
 * <p>
 * 운동방 {@code Participant}와 달리 참가자 토큰·캐릭터가 없고, 판마다 새로 만든다.
 * 같은 사람이 여러 판을 하면 판마다 한 줄씩 생긴다.
 */
@Getter
@Entity
@Table(name = "univ_contest_player")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ContestPlayer extends BaseTimeEntity {

    /** 판 참가 식별값 (응답의 playerId) */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 들어온 판 */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "game_id", nullable = false)
    private ContestGame game;

    /** 닉네임. 중복될 수 있다 */
    @Column(name = "nickname", nullable = false, length = 20)
    private String nickname;

    /** 학교 식별값. 학교 목록은 프론트가 갖고, 서버는 받은 문자열을 그대로 저장해 묶기만 한다 */
    @Column(name = "school_id", nullable = false, length = 30)
    private String schoolId;

    /** 한 판 스쿼트 횟수. null이면 측정 안 됨(자세가 한 번도 인식되지 않음)이고, 0은 측정했는데 0회다 */
    @Column(name = "reps")
    private Integer reps;

    /** 결과를 냈는지 여부. 측정 안 됨(reps=null)으로 낸 경우도 true다 */
    @Column(name = "submitted", nullable = false)
    private boolean submitted;

    ContestPlayer(ContestGame game, String nickname, String schoolId) {
        this.game = game;
        this.nickname = nickname;
        this.schoolId = schoolId;
    }

    /**
     * 한 판 결과를 기록한다. 이미 냈으면 처음 값을 그대로 두고 아무것도 하지 않는다(프론트 재전송 대비).
     *
     * @param reps 횟수. 측정 안 됨이면 null
     * @return 이번 호출로 기록했으면 true, 이미 냈으면 false
     */
    public boolean submitResult(Integer reps) {
        if (submitted) {
            return false;
        }
        this.reps = reps;
        this.submitted = true;
        return true;
    }

    /**
     * 순위·학교 합계에 넣을 수 있는 기록인지 확인한다. 결과를 냈고 측정된(reps가 null이 아닌) 기록만 넣는다.
     */
    public boolean hasMeasuredResult() {
        return submitted && reps != null;
    }
}
