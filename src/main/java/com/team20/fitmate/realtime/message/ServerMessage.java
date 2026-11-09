package com.team20.fitmate.realtime.message;

/**
 * 서버가 보내는 모든 실시간 메시지의 공통 봉투.
 * <p>
 * 이슈 #2의 공통 계약(타입, 방 식별값, 참가자 식별값, 이벤트 시각)을 그대로 담는다.
 * 재방문 토큰은 어떤 메시지에도 넣지 않는다.
 *
 * @param type          메시지 종류
 * @param roomId        운동방 식별값. 입장 전 오류처럼 방이 정해지지 않았으면 null
 * @param participantId 이 이벤트의 주인공 참가자 식별값. 스냅샷은 입장한 본인, 입장 전 오류는 null
 * @param sentAt        서버가 이벤트를 만든 시각(ISO-8601, UTC)
 * @param payload       종류별 내용. PONG은 null
 */
public record ServerMessage(
        ServerMessageType type,
        Long roomId,
        Long participantId,
        String sentAt,
        Object payload
) {
}
