package com.team20.fitmate.realtime.message;

/**
 * 실시간 서버가 프론트로 보내는 메시지 종류.
 * <p>
 * 형식과 예시는 {@code docs/실시간_메시지_형식.md}에 있다. 종류를 추가하면 문서도 함께 고친다.
 */
public enum ServerMessageType {

    /** 입장한 본인에게 보내는 방 전체 상태(정원과 현재 참가자 목록) */
    ROOM_SNAPSHOT,

    /** 다른 참가자가 새로 입장함 */
    PARTICIPANT_JOINED,

    /** 다른 참가자가 퇴장함(직접 퇴장 또는 재접속 대기 시간 초과) */
    PARTICIPANT_LEFT,

    /** 다른 참가자의 현재 운동·진행 상태가 바뀜 */
    PARTICIPANT_STATUS,

    /** 다른 참가자의 동작 정보. 받은 그대로 전달하며 DB에 저장하지 않는다 */
    PARTICIPANT_POSE,

    /** 다른 참가자의 연결 상태가 바뀜(연결됨 ↔ 연결 불안정) */
    CONNECTION_STATUS,

    /** PING에 대한 응답 */
    PONG,

    /** 요청을 처리하지 못함. 연결을 닫는 오류인지는 payload의 closed로 알려 준다 */
    ERROR
}
