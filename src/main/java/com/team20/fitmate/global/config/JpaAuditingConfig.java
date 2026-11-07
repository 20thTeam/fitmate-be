package com.team20.fitmate.global.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

/**
 * JPA Auditing 활성화 설정.
 * <p>
 * {@code BaseTimeEntity}의 {@code @CreatedDate}, {@code @LastModifiedDate}가 자동으로 채워지게 한다.
 * 메인 애플리케이션 클래스가 아니라 별도 설정에 두어, 웹 계층만 띄우는 테스트에서 JPA 설정이 끼어들지 않게 한다.
 */
@Configuration
@EnableJpaAuditing
public class JpaAuditingConfig {
}
