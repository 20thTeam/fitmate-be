package com.team20.fitmate.domain.workout.entity;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 운동 기록 종류. 별도 컬럼 없이 루틴 유무로 정한다.
 */
@Getter
@RequiredArgsConstructor
public enum WorkoutKind {

    /** 친구 운동방에서 루틴으로 한 운동 */
    ROUTINE("루틴 운동"),

    /** 공개 파티(선셋 에어로빅)에서 루틴 없이 한 운동 */
    PUBLIC_PARTY("공개 파티");

    /** 화면 표시용 이름 */
    private final String displayName;
}
