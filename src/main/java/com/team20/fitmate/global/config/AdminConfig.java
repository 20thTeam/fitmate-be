package com.team20.fitmate.global.config;

import com.team20.fitmate.domain.admin.auth.AdminKeyInterceptor;
import com.team20.fitmate.domain.admin.auth.AdminProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 운영 API 설정. 운영 키 설정값({@code admin.*})을 읽고, {@code /api/admin/**}에 운영 키 확인을 건다.
 */
@Configuration
@RequiredArgsConstructor
@EnableConfigurationProperties(AdminProperties.class)
public class AdminConfig implements WebMvcConfigurer {

    private final AdminProperties adminProperties;

    /**
     * 운영 API 경로에만 운영 키 확인 인터셉터를 등록한다. 참가자용 API에는 영향이 없다.
     */
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new AdminKeyInterceptor(adminProperties))
                .addPathPatterns("/api/admin/**");
    }
}
