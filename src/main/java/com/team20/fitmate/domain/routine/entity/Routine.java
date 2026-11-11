package com.team20.fitmate.domain.routine.entity;

import com.team20.fitmate.domain.participant.entity.Participant;
import com.team20.fitmate.domain.workout.entity.Workout;
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
import jakarta.persistence.OneToOne;
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
 * 첫 회차와 2회차를 같은 구조로 저장한다. 2회차 이상은 근거 운동 기록과 조정 유형·사유를 함께 가진다.
 * 근거 운동 기록 하나에서는 다음 회차 루틴을 하나만 만든다({@code based_on_workout_id} 유니크).
 */
@Getter
@Entity
@Table(name = "routine")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Routine extends BaseTimeEntity {

    /** 조정 사유 최대 길이 */
    public static final int ADJUSTMENT_REASON_MAX_LENGTH = 1000;

    /** 조정 사유를 운동별로 나누는 구분자 */
    private static final String REASON_DELIMITER = "\n";

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

    /** 다음 회차 추천의 근거가 된 이전 회차 운동 기록. 첫 회차는 null */
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "based_on_workout_id", unique = true)
    private Workout basedOnWorkout;

    /** 조정 유형. 첫 회차는 null */
    @Enumerated(EnumType.STRING)
    @Column(name = "adjustment_type", length = 20)
    private AdjustmentType adjustmentType;

    /** 조정 사유. 운동마다 한 줄씩 줄바꿈으로 이어 붙인다. 첫 회차는 null */
    @Column(name = "adjustment_reason", length = ADJUSTMENT_REASON_MAX_LENGTH)
    private String adjustmentReason;

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
     * 이전 회차 운동 기록을 근거로 다음 회차 루틴을 만든다. 구성 운동은 {@link #addExercise}로 붙인다.
     *
     * @param basedOnWorkout 근거가 된 종료된 운동 기록
     * @param reasons        운동별 조정 사유 (순서대로)
     */
    public static Routine nextRound(Workout basedOnWorkout, AdjustmentType adjustmentType, List<String> reasons) {
        Routine previous = basedOnWorkout.getRoutine();
        Routine routine = new Routine(basedOnWorkout.getParticipant(), previous.getRoundNo() + 1,
                previous.getGoal(), RoutineStatus.COMPLETED);
        routine.basedOnWorkout = basedOnWorkout;
        routine.adjustmentType = adjustmentType;
        routine.adjustmentReason = String.join(REASON_DELIMITER, reasons);
        return routine;
    }

    /**
     * 운동별 조정 사유를 순서대로 돌려준다. 첫 회차면 빈 목록이다.
     */
    public List<String> getAdjustmentReasons() {
        if (adjustmentReason == null || adjustmentReason.isEmpty()) {
            return List.of();
        }
        return List.of(adjustmentReason.split(REASON_DELIMITER));
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
