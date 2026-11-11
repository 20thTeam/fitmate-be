package com.team20.fitmate.global.config;

import com.team20.fitmate.domain.party.schedule.PublicPartyProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * 공개 파티 설정({@code public-party.*})을 읽는다. 오픈·종료 시각은 PM 확정 전 임시값이다.
 */
@Configuration
@EnableConfigurationProperties(PublicPartyProperties.class)
public class PublicPartyConfig {
}
