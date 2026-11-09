package com.team20.fitmate.domain.xp.entity;

/**
 * 운동 결과에서 팀 경험치가 어떤 상태인지. 프론트는 {@link #ACCRUED}일 때만 경험치 수치를 보여준다.
 */
public enum XpStatus {

    /** 적립됨 */
    ACCRUED,

    /** 경험치 산식이 정해지지 않아 적립하지 않음. 임의 수치를 보여주지 않는다 */
    FORMULA_UNDECIDED,

    /** 운동방에 입장하지 않고 운동해 적립할 팀이 없음 */
    NO_TEAM,

    /** 아직 운동이 끝나지 않음 */
    NOT_FINISHED
}
