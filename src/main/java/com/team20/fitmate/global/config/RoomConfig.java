package com.team20.fitmate.global.config;

import com.team20.fitmate.domain.room.config.RoomProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * 운동방 설정값({@code room.*})을 읽는다.
 */
@Configuration
@EnableConfigurationProperties(RoomProperties.class)
public class RoomConfig {
}
