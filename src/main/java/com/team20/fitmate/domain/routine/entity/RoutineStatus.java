package com.team20.fitmate.domain.routine.entity;

/**
 * 루틴 추천 상태. 화면은 이 값으로 추천 준비·완료·불가 상태를 나눠 보여준다.
 */
public enum RoutineStatus {

    /** 추천 준비. 추천을 만드는 중이다 (비동기 추천을 붙일 때 쓴다) */
    PREPARING,

    /** 추천 완료. 운동 목록이 있다 */
    COMPLETED,

    /** 추천 불가. 운동 목록을 내려주지 않고 대체 안내를 보여준다 */
    UNAVAILABLE
}
