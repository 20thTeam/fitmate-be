package com.team20.fitmate.domain.contest;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

/**
 * 챌린지 테스트용 시계. 실제 시각과 관계없이 고정된 시각에서 시작하고, 테스트에서 앞으로 돌릴 수 있다.
 */
@TestConfiguration
public class ContestTestClock {

    /** 테스트 시작 시각 */
    public static final Instant BASE = Instant.parse("2026-11-13T05:00:00Z");

    @Bean
    @Primary
    MutableClock contestMutableClock() {
        return new MutableClock(BASE);
    }

    /**
     * 테스트에서 시각을 바꿀 수 있는 시계.
     */
    public static final class MutableClock extends Clock {

        private volatile Instant instant;

        MutableClock(Instant instant) {
            this.instant = instant;
        }

        /** 시각을 지정한다 */
        public void set(Instant instant) {
            this.instant = instant;
        }

        /** 시각을 앞으로 돌린다 */
        public void advance(Duration duration) {
            this.instant = instant.plus(duration);
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
