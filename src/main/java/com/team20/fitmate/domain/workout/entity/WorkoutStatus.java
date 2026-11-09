package com.team20.fitmate.domain.workout.entity;

/**
 * 운동 기록 상태.
 */
public enum WorkoutStatus {

    /** 진행 중. 운동을 시작했고 아직 종료하지 않았다 */
    IN_PROGRESS,

    /** 종료. 수행 기록이 확정되었다. 이후 다시 제출할 수 없다 */
    FINISHED
}
