package com.team20.fitmate.domain.exercise.entity;

/**
 * 운동의 측정 방식.
 */
public enum MeasureType {

    /** 횟수형. 유효 횟수로 측정한다 (예: 스쿼트) */
    COUNT,

    /** 시간형. 유지한 시간(초)으로 측정한다 (예: 플랭크) */
    TIME
}
