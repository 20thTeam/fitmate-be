package com.team20.fitmate.realtime.message;

/**
 * JOIN 메시지 내용. 토큰은 URL이 아니라 이 메시지로 받는다(프록시 접근 로그에 남지 않게).
 *
 * @param token  참가자 생성 때 받은 재방문 토큰
 * @param roomId 들어갈 운동방 식별값. REST 입장({@code POST /api/rooms/{roomId}/enter})을 먼저 마친 방이어야 한다
 */
public record JoinPayload(String token, Long roomId) {

    /**
     * 필수 값이 모두 있는지 확인한다.
     */
    public boolean isValid() {
        return token != null && !token.isBlank() && roomId != null;
    }
}
