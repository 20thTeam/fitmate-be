package com.team20.fitmate.domain.routine.entity;

import com.team20.fitmate.domain.exercise.entity.Exercise;
import com.team20.fitmate.domain.exercise.entity.MeasureType;
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
 * 루틴을 이루는 운동 하나. 순서, 목표 횟수 또는 시간, 세트를 가진다.
 * <p>
 * 횟수형은 {@code targetCount}만, 시간형은 {@code targetSeconds}만 채운다. 나머지는 null이다.
 */
@Getter
@Entity
@Table(name = "routine_exercise")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RoutineExercise extends BaseTimeEntity {

    /** 식별값 */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 소속 루틴 */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "routine_id", nullable = false)
    private Routine routine;

    /** 수행할 운동 */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "exercise_id", nullable = false)
    private Exercise exercise;

    /** 루틴 안에서의 순서. 1부터 시작한다 */
    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    /** 목표 횟수. 횟수형만 채운다 */
    @Column(name = "target_count")
    private Integer targetCount;

    /** 목표 시간(초). 시간형만 채운다 */
    @Column(name = "target_seconds")
    private Integer targetSeconds;

    /** 세트 수 */
    @Column(name = "target_sets", nullable = false)
    private int targetSets;

    /** 운동 설명 태그 (예: 자세 개선 · 하체 근력 강화). 루틴 확인 화면에 보여준다 */
    @Column(name = "tag", length = 50)
    private String tag;

    private RoutineExercise(Exercise exercise, int sortOrder, Integer targetCount, Integer targetSeconds,
                            int targetSets, String tag) {
        this.exercise = exercise;
        this.sortOrder = sortOrder;
        this.targetCount = targetCount;
        this.targetSeconds = targetSeconds;
        this.targetSets = targetSets;
        this.tag = tag;
    }

    /**
     * 운동의 측정 방식에 맞춰 구성 운동을 만든다.
     *
     * @param amount 횟수형이면 목표 횟수, 시간형이면 목표 시간(초)
     * @param tag    운동 설명 태그. 없으면 null
     */
    public static RoutineExercise of(Exercise exercise, int sortOrder, int amount, int targetSets, String tag) {
        if (exercise.getMeasureType() == MeasureType.COUNT) {
            return new RoutineExercise(exercise, sortOrder, amount, null, targetSets, tag);
        }
        return new RoutineExercise(exercise, sortOrder, null, amount, targetSets, tag);
    }

    /**
     * 설명 태그 없이 구성 운동을 만든다.
     */
    public static RoutineExercise of(Exercise exercise, int sortOrder, int amount, int targetSets) {
        return of(exercise, sortOrder, amount, targetSets, null);
    }

    /**
     * 소속 루틴을 정한다. {@link Routine#addExercise}에서만 부른다.
     */
    void assignRoutine(Routine routine) {
        this.routine = routine;
    }
}
