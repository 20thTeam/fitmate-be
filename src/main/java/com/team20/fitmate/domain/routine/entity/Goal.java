package com.team20.fitmate.domain.routine.entity;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 운동 목표. 참가자는 회차마다 네 가지 중 하나를 고른다.
 */
@Getter
@RequiredArgsConstructor
public enum Goal {

    /** 체중 감량 */
    WEIGHT_LOSS("체중 감량"),

    /** 자세 개선 */
    POSTURE("자세 개선"),

    /** 근력 강화 */
    STRENGTH("근력 강화"),

    /** 체력 증진 */
    STAMINA("체력 증진");

    /** 화면에 보여줄 한글 이름 */
    private final String displayName;
}
