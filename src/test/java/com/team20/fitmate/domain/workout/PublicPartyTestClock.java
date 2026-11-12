package com.team20.fitmate.domain.workout;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

/**
 * 공개 파티 운동 기록 테스트용 시계. 실제 시각과 관계없이 공개 파티가 열린 시각(기본 설정 22:00~23:00 KST 안)에서 시작하고,
 * 테스트에서 닫힌 시각으로 옮길 수 있다.
 */
@TestConfiguration
public class PublicPartyTestClock {

    /** 기본 설정(22:00~23:00 KST) 기준 열려 있는 시각 */
    public static final Instant OPEN = Instant.parse("2026-11-11T13:30:00Z");

    /** 기본 설정 기준 닫혀 있는 시각 (21:00 KST) */
    public static final Instant CLOSED = Instant.parse("2026-11-11T12:00:00Z");

    @Bean
    @Primary
    MutableClock publicPartyMutableClock() {
        return new MutableClock(OPEN);
    }

    /**
     * 테스트에서 시각을 바꿀 수 있는 시계.
     */
    public static final class MutableClock extends Clock {

        private volatile Instant instant;

        MutableClock(Instant instant) {
            this.instant = instant;
        }

        public void set(Instant instant) {
            this.instant = instant;
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }
}
