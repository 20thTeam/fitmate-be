package com.team20.fitmate.domain.routine.entity;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 2회차 이상 루틴의 조정 유형. 이전 회차 기록을 평가한 결과다(기능 8.2).
 */
@Getter
@RequiredArgsConstructor
public enum AdjustmentType {

    /** 충분히 수행한 운동이 있어 수행량을 올림 */
    UP("상향"),

    /** 수행이 부족하거나 안전 상한이라 같은 수행량을 유지함 */
    KEEP("유지"),

    /** 판단할 기록이 없어 분석하지 못함. 수행량은 올리지 않는다 */
    UNAVAILABLE("분석 불가");

    /** 화면에 보여줄 한글 이름 */
    private final String displayName;
}
