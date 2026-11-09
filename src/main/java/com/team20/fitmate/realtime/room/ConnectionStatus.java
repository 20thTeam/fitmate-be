package com.team20.fitmate.realtime.room;

/**
 * 참가자의 실시간 연결 상태.
 * <p>
 * 연결이 끊기면 바로 퇴장시키지 않고 {@link #UNSTABLE}로 표시한 뒤 재접속을 기다린다.
 * 마지막 진행 상태를 현재처럼 단정하지 않도록 프론트는 이 값을 함께 표시한다.
 */
public enum ConnectionStatus {

    /** 정상 연결 */
    CONNECTED,

    /** 연결이 끊겼거나 응답이 없어 재접속을 기다리는 중 */
    UNSTABLE
}
