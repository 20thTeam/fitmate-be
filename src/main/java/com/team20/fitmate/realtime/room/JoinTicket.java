package com.team20.fitmate.realtime.room;

/**
 * DB 확인을 마친 입장 정보. 실시간 계층은 이 값만 들고 다니고 엔티티는 들고 다니지 않는다
 * (트랜잭션 밖에서 LAZY 연관을 건드리지 않기 위해서다).
 *
 * @param roomId        운동방 식별값
 * @param capacity      운동방 정원
 * @param participantId 참가자 식별값
 * @param nickname      닉네임
 * @param characterCode 캐릭터 코드
 */
public record JoinTicket(
        Long roomId,
        int capacity,
        Long participantId,
        String nickname,
        String characterCode
) {
}
