package com.team20.fitmate.global.config;

import com.team20.fitmate.domain.exercise.init.ExerciseGuideProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * 운동 설정. 운동별 가이드 시범 참조({@code exercise-guide.*})를 읽는다.
 */
@Configuration
@EnableConfigurationProperties(ExerciseGuideProperties.class)
public class ExerciseConfig {
}
