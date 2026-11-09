package com.team20.fitmate.domain.xp.entity;

import com.team20.fitmate.domain.participant.entity.Participant;
import com.team20.fitmate.domain.room.entity.Team;
import com.team20.fitmate.domain.workout.entity.Workout;
import com.team20.fitmate.global.common.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 팀 경험치 적립 기록. 운동 기록 하나에 한 번만 적립되도록 {@code workout_id}를 유니크로 둔다.
 */
@Getter
@Entity
@Table(name = "team_xp_log")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TeamXpLog extends BaseTimeEntity {

    /** 적립 기록 식별값 */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 경험치가 쌓인 팀 */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "team_id", nullable = false)
    private Team team;

    /** 경험치를 만든 참가자 */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "participant_id", nullable = false)
    private Participant participant;

    /** 적립 근거가 된 운동 기록. 중복 적립 방지를 위해 유니크 */
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "workout_id", nullable = false, unique = true)
    private Workout workout;

    /** 적립 경험치. 하루 최대치를 넘으면 0일 수 있다 */
    @Column(name = "earned_xp", nullable = false)
    private int earnedXp;

    /** 적립 직후 팀 누적 경험치. 이번 운동으로 레벨이 올랐는지 보여줄 때 쓴다 */
    @Column(name = "team_total_xp_after")
    private Integer teamTotalXpAfter;

    private TeamXpLog(Team team, Participant participant, Workout workout, int earnedXp) {
        this.team = team;
        this.participant = participant;
        this.workout = workout;
        this.earnedXp = earnedXp;
    }

    /**
     * 운동 기록으로 팀 경험치 적립 기록을 만든다.
     */
    public static TeamXpLog of(Team team, Workout workout, int earnedXp) {
        return new TeamXpLog(team, workout.getParticipant(), workout, earnedXp);
    }

    /**
     * 적립 직후 팀 누적 경험치를 남긴다.
     */
    public void recordTeamTotal(int teamTotalXpAfter) {
        this.teamTotalXpAfter = teamTotalXpAfter;
    }
}
