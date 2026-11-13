package com.team20.fitmate.domain.contest.entity;

/**
 * 학교 대항 스쿼트 챌린지 판의 상태. DB에 저장하지 않고 시작 시각·제출 여부·현재 시각으로 매번 계산한다.
 * <p>
 * {@code WAITING} → {@code COUNTDOWN} → {@code RUNNING} → {@code FINISHED} 순서로만 바뀐다.
 */
public enum ContestGameStatus {

    /** 모집 중. 아직 아무도 시작을 누르지 않았다 */
    WAITING,

    /** 시작을 눌렀고 운동 시작 시각(startAt) 전이다 */
    COUNTDOWN,

    /** 운동 중(startAt ~ startAt+20초)이거나, 운동이 끝나고 결과 제출을 기다리는 중 */
    RUNNING,

    /** 모든 참가자가 결과를 냈거나 startAt+30초가 지났다. 이후 결과는 받지 않는다 */
    FINISHED
}
