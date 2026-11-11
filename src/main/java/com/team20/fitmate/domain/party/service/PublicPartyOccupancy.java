package com.team20.fitmate.domain.party.service;

/**
 * 공개 파티에 지금 들어와 있는 참가자 수를 알려 주는 쪽. 실시간 계층(운동방 레지스트리)이 구현한다.
 * <p>
 * 도메인이 실시간 계층 클래스를 직접 알지 않도록 인터페이스로 둔다.
 */
public interface PublicPartyOccupancy {

    /**
     * 공개 파티 실시간 방의 현재 참가자 수. 재접속을 기다리는 연결 불안정 참가자도 센다.
     *
     * @param partyId 공개 파티 식별값
     * @return 참가자 수. 아무도 없으면 0
     */
    int countParticipants(Long partyId);
}
