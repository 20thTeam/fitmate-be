package com.team20.fitmate.domain.admin.service;

import java.util.Set;

/**
 * 운영 화면이 실시간 계층에서 읽는 접속 정보.
 * <p>
 * 운영 도메인이 실시간 레지스트리 구현을 직접 알지 않도록 인터페이스로 둔다. 구현은 {@code realtime} 패키지에 있다.
 */
public interface RoomPresenceReader {

    /**
     * 운동방에 지금 웹소켓으로 정상 연결된(연결 불안정이 아닌) 참가자 식별값.
     */
    Set<Long> connectedParticipantIds(Long roomId);

    /**
     * 모든 실시간 연결을 닫고 방 상태를 비운다. 시연 데이터 초기화 뒤 지워진 참가자가 방에 남지 않게 한다.
     */
    void disconnectAll();

    /**
     * 챌린지 판(이슈 #34) 웹소켓 연결만 모두 닫는다. 챌린지 기록 초기화(이슈 #41) 뒤 지워진 판에 연결이 남지 않게 한다.
     * 친구 운동방·공개 파티 연결은 그대로 둔다.
     */
    void closeAllContestGames();
}
