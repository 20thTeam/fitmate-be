package com.team20.fitmate.realtime.room;

/**
 * DB 확인을 마친 입장 정보. 실시간 계층은 이 값만 들고 다니고 엔티티는 들고 다니지 않는다
 * (트랜잭션 밖에서 LAZY 연관을 건드리지 않기 위해서다).
 *
 * @param space         들어갈 실시간 방(운동방 또는 공개 파티)
 * @param capacity      정원. 공개 파티처럼 정원이 없으면 null
 * @param participantId 참가자 식별값
 * @param nickname      닉네임
 * @param characterCode 캐릭터 코드
 */
public record JoinTicket(
        SpaceKey space,
        Integer capacity,
        Long participantId,
        String nickname,
        String characterCode
) {

    /**
     * 친구 파티 운동방 입장 정보.
     */
    public JoinTicket(Long roomId, int capacity, Long participantId, String nickname, String characterCode) {
        this(SpaceKey.room(roomId), capacity, participantId, nickname, characterCode);
    }

    /**
     * 정원 없는 공개 파티 입장 정보.
     */
    public static JoinTicket publicParty(Long partyId, Long participantId, String nickname, String characterCode) {
        return new JoinTicket(SpaceKey.publicParty(partyId), null, participantId, nickname, characterCode);
    }
}
