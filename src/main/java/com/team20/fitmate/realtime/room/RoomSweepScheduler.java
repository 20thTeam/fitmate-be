package com.team20.fitmate.realtime.room;

import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 시간이 지난 연결과 참가자를 주기적으로 정리한다. 실제 판단은 {@link RoomSessionRegistry#sweep()}이 한다.
 * <p>
 * 주기는 {@code realtime.sweep-interval-ms}(기본 1초, 임시값)다.
 */
@Component
@RequiredArgsConstructor
public class RoomSweepScheduler {

    private final RoomSessionRegistry registry;

    /**
     * JOIN 대기·응답 대기·재접속 대기 시간이 지난 대상을 정리한다.
     */
    @Scheduled(fixedDelayString = "${realtime.sweep-interval-ms:1000}")
    public void sweep() {
        registry.sweep();
    }
}
