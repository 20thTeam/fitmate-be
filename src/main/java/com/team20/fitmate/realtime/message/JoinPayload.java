package com.team20.fitmate.realtime.message;

/**
 * JOIN 메시지 내용. 토큰은 URL이 아니라 이 메시지로 받는다(프록시 접근 로그에 남지 않게).
 * <p>
 * {@code roomId}와 {@code partyId} 중 <b>하나만</b> 보낸다.
 *
 * @param token   참가자 생성 때 받은 재방문 토큰
 * @param roomId  들어갈 친구 파티 운동방 식별값. REST 입장({@code POST /api/rooms/{roomId}/enter})을 먼저 마친 방이어야 한다
 * @param partyId 들어갈 공개 파티 식별값(이슈 #11). {@code GET /api/public-parties/current}의 partyId. REST 입장은 필요 없다
 */
public record JoinPayload(String token, Long roomId, Long partyId) {

    /**
     * 토큰이 있고, 운동방·공개 파티 중 정확히 하나를 골랐는지 확인한다.
     * getter로 직렬화되지 않도록 is·get으로 시작하지 않는 이름을 쓴다.
     */
    public boolean hasValidFormat() {
        return token != null && !token.isBlank() && (roomId == null) != (partyId == null);
    }

    /** 공개 파티 입장 요청인지 */
    public boolean targetsPublicParty() {
        return partyId != null;
    }
}
