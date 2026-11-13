package com.team20.fitmate.realtime.room;

import com.team20.fitmate.domain.contest.service.ContestService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * 끝난(FINISHED) 챌린지 판의 실시간 연결을 주기적으로 정리한다 (이슈 #34).
 * <p>
 * 판 상태는 DB와 서버 시각으로 계산하므로, 레지스트리 잠금 밖에서 {@link ContestService}에 물어본 뒤
 * 끝났거나 없어진 판만 {@link RoomSessionRegistry#closeContestGame(Long)}으로 닫는다.
 * 주기는 다른 실시간 정리와 같은 {@code realtime.sweep-interval-ms}(기본 1초)다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ContestGameSweepScheduler {

    private final RoomSessionRegistry registry;
    private final ContestService contestService;

    /**
     * 들어와 있는 판 중 끝난 판의 연결을 닫는다. 들어와 있는 판이 없으면 DB를 읽지 않는다.
     */
    @Scheduled(fixedDelayString = "${realtime.sweep-interval-ms:1000}")
    public void sweep() {
        Set<Long> active = registry.activeContestGameIds();
        if (active.isEmpty()) {
            return;
        }
        try {
            contestService.findClosedGameIds(active).forEach(registry::closeContestGame);
        } catch (RuntimeException e) {
            // 한 번 실패해도 다음 주기에 다시 확인한다
            log.warn("챌린지 판 연결 정리 실패", e);
        }
    }
}
