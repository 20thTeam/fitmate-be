package com.team20.fitmate.global.config;

import com.team20.fitmate.realtime.room.RealtimeProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.time.Clock;

/**
 * 실시간 계층의 공통 빈. 시간 설정({@code realtime.*})을 읽고, 정리 스케줄러를 켜고, 시각 기준을 둔다.
 * <p>
 * {@link WebSocketConfig}와 나눈 이유: 핸들러가 이 빈들에 기대므로 같은 클래스에 두면 순환 의존이 생긴다.
 */
@Configuration
@EnableScheduling
@EnableConfigurationProperties(RealtimeProperties.class)
public class RealtimeConfig {

    /**
     * 실시간 계층의 시각 기준(UTC). 테스트에서 시간을 바꿔 끼울 수 있게 빈으로 둔다.
     */
    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
