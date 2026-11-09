package com.team20.fitmate.domain.routine.entity;

import com.team20.fitmate.domain.participant.entity.Participant;
import com.team20.fitmate.global.common.BaseTimeEntity;
import jakarta.persistence.CascadeType;
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
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * 루틴. 한 회차에 수행할 운동 2~3개와 순서, 수행량, 세트의 묶음이다.
 * <p>
 * 첫 회차와 2회차를 같은 구조로 저장한다. 2회차의 근거 운동 기록과 조정 유형·사유는
 * 2회차 추천 슬라이스(S9)에서 추가한다.
 */
@Getter
@Entity
@Table(name = "routine")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Routine extends BaseTimeEntity {

    /** 루틴 식별값 */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 이 루틴을 추천받은 참가자 */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "participant_id", nullable = false)
    private Participant participant;

    /** 회차. 첫 회차는 1 */
    @Column(name = "round_no", nullable = false)
    private int roundNo;

    /** 선택한 목표 */
    @Enumerated(EnumType.STRING)
    @Column(name = "goal", nullable = false, length = 20)
    private Goal goal;

    /** 추천 상태 */
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private RoutineStatus status;

    /** 구성 운동. 순서대로 정렬한다. 추천 불가면 비어 있다 */
    @OneToMany(mappedBy = "routine", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("sortOrder ASC")
    private List<RoutineExercise> routineExercises = new ArrayList<>();

    private Routine(Participant participant, int roundNo, Goal goal, RoutineStatus status) {
        this.participant = participant;
        this.roundNo = roundNo;
        this.goal = goal;
        this.status = status;
    }

    /**
     * 추천이 끝난 루틴을 만든다. 구성 운동은 {@link #addExercise}로 붙인다.
     */
    public static Routine completed(Participant participant, int roundNo, Goal goal) {
        return new Routine(participant, roundNo, goal, RoutineStatus.COMPLETED);
    }

    /**
     * 추천 불가 루틴을 만든다. 운동을 붙이지 않는다.
     */
    public static Routine unavailable(Participant participant, int roundNo, Goal goal) {
        return new Routine(participant, roundNo, goal, RoutineStatus.UNAVAILABLE);
    }

    /**
     * 구성 운동을 하나 붙인다. 추천 완료 루틴에만 붙일 수 있다.
     */
    public void addExercise(RoutineExercise routineExercise) {
        if (status != RoutineStatus.COMPLETED) {
            throw new IllegalStateException("추천 완료 루틴에만 운동을 붙일 수 있다");
        }
        routineExercises.add(routineExercise);
        routineExercise.assignRoutine(this);
    }

    /**
     * 이 루틴이 해당 참가자의 것인지 확인한다.
     */
    public boolean isOwnedBy(Participant participant) {
        return this.participant.getId().equals(participant.getId());
    }
}
