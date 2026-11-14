package com.team20.fitmate.realtime.room;

import com.team20.fitmate.domain.admin.service.RoomPresenceReader;
import com.team20.fitmate.realtime.message.ParticipantView;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.stream.Collectors;

/**
 * 실시간 레지스트리({@link RoomSessionRegistry})에서 운영 화면용 접속 정보를 읽는 구현.
 * <p>
 * 레지스트리의 공개 메서드만 쓰며 상태를 바꾸는 일은 {@link #disconnectAll()} 하나뿐이다.
 */
@Component
@RequiredArgsConstructor
public class RegistryRoomPresenceReader implements RoomPresenceReader {

    private final RoomSessionRegistry registry;

    /**
     * 방 참가자 중 연결 상태가 {@link ConnectionStatus#CONNECTED}인 참가자만 고른다.
     * 재접속을 기다리는(연결 불안정) 참가자는 지금 연결된 것으로 보지 않는다.
     */
    @Override
    public Set<Long> connectedParticipantIds(Long roomId) {
        return registry.participants(roomId).stream()
                .filter(view -> view.connectionStatus() == ConnectionStatus.CONNECTED)
                .map(ParticipantView::participantId)
                .collect(Collectors.toUnmodifiableSet());
    }

    /**
     * 레지스트리의 모든 연결을 닫고 비운다. 프론트는 연결이 닫히면 다시 입장을 시도하고, 참가자가 없으면 프로필을 다시 만든다.
     */
    @Override
    public void disconnectAll() {
        registry.clear();
    }

    /**
     * 들어와 있는 챌린지 판을 모두 닫는다. 닫힘 코드는 판 종료와 같은 1000 CONTEST_GAME_FINISHED라 프론트가 다시 연결하지 않는다.
     */
    @Override
    public void closeAllContestGames() {
        registry.activeContestGameIds().forEach(registry::closeContestGame);
    }
}
