package com.team20.fitmate.realtime.message;

/**
 * JOIN 메시지 내용. 토큰은 URL이 아니라 이 메시지로 받는다(프록시 접근 로그에 남지 않게).
 * <p>
 * {@code roomId}·{@code partyId}·{@code contestGameId} 중 <b>하나만</b> 보낸다.
 * 운동방·공개 파티는 {@code token}으로, 챌린지 판은 참가자 토큰이 없어 {@code playerId}로 들어온다.
 *
 * @param token         참가자 생성 때 받은 재방문 토큰. 운동방·공개 파티 입장에 쓴다
 * @param roomId        들어갈 친구 파티 운동방 식별값. REST 입장({@code POST /api/rooms/{roomId}/enter})을 먼저 마친 방이어야 한다
 * @param partyId       들어갈 공개 파티 식별값(이슈 #11). {@code GET /api/public-parties/current}의 partyId. REST 입장은 필요 없다
 * @param contestGameId 들어갈 챌린지 판 식별값(이슈 #34). {@code POST /api/univ-contest/games/join}이 준 gameId ("17"·17 모두 받는다)
 * @param playerId      챌린지 판 참가 식별값. 같은 join 응답의 playerId
 */
public record JoinPayload(String token, Long roomId, Long partyId, Long contestGameId, Long playerId) {

    /**
     * 들어갈 곳을 정확히 하나 골랐고, 그곳에 맞는 신원 값(운동방·공개 파티는 token, 챌린지 판은 playerId)이 있는지 확인한다.
     * getter로 직렬화되지 않도록 is·get으로 시작하지 않는 이름을 쓴다.
     */
    public boolean hasValidFormat() {
        int targets = (roomId != null ? 1 : 0) + (partyId != null ? 1 : 0) + (contestGameId != null ? 1 : 0);
        if (targets != 1) {
            return false;
        }
        if (targetsContestGame()) {
            return playerId != null;
        }
        return token != null && !token.isBlank();
    }

    /** 공개 파티 입장 요청인지 */
    public boolean targetsPublicParty() {
        return partyId != null;
    }

    /** 챌린지 판 입장 요청인지 */
    public boolean targetsContestGame() {
        return contestGameId != null;
    }
}
