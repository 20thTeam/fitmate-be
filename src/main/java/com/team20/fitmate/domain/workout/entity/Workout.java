package com.team20.fitmate.domain.workout.entity;

import com.team20.fitmate.domain.participant.entity.Participant;
import com.team20.fitmate.domain.room.entity.Room;
import com.team20.fitmate.domain.routine.entity.Routine;
import com.team20.fitmate.global.common.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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

import java.time.LocalDateTime;

/**
 * 운동 기록. 참가자가 한 회차를 수행한 기록이며, 이 식별값으로 팀 경험치 중복 적립을 막는다.
 * <p>
 * S3에서는 수행 기록({@link ExerciseRecord})이 붙을 수 있도록 뼈대만 둔다.
 * 시작·종료 API와 재제출 거부는 운동 진행 슬라이스(S7)에서, 분석 상태는 결과 슬라이스(S8)에서 추가한다.
 */
@Getter
@Entity
@Table(name = "workout")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Workout extends BaseTimeEntity {

    /** 운동 기록 식별값 */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 운동한 참가자 */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "participant_id", nullable = false)
    private Participant participant;

    /** 수행한 루틴. 공개 파티처럼 루틴 없이 운동하면 null */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "routine_id")
    private Routine routine;

    /** 운동한 운동방. 공개 파티면 null */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "room_id")
    private Room room;

    /** 상태 */
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private WorkoutStatus status;

    /** 시작 시각 */
    @Column(name = "started_at", nullable = false)
    private LocalDateTime startedAt;

    /** 종료 시각. 진행 중이면 null */
    @Column(name = "finished_at")
    private LocalDateTime finishedAt;

    private Workout(Participant participant, Routine routine, Room room, LocalDateTime startedAt) {
        this.participant = participant;
        this.routine = routine;
        this.room = room;
        this.status = WorkoutStatus.IN_PROGRESS;
        this.startedAt = startedAt;
    }

    /**
     * 운동방에서 루틴으로 운동을 시작한다.
     */
    public static Workout start(Participant participant, Routine routine, Room room, LocalDateTime startedAt) {
        return new Workout(participant, routine, room, startedAt);
    }
}
