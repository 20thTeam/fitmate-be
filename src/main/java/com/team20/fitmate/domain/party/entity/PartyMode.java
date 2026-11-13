package com.team20.fitmate.domain.party.entity;

/**
 * 공개 파티 운영 모드(이슈 #37). 운영 화면에서 바꾼다.
 */
public enum PartyMode {

    /** 운영 시각대로 열고 닫는다. 기본값이며 서버를 다시 시작하면 이 값으로 돌아간다 */
    AUTO,

    /** 시각과 상관없이 연다. 운영자가 바꿀 때까지 열려 있다 */
    OPEN,

    /** 시각과 상관없이 닫는다. 운영 시각 안이어도 닫힌다 */
    CLOSED
}
