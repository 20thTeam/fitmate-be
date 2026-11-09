package com.team20.fitmate.global.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 웹 MVC 공통 설정. 현재는 CORS만 다룬다.
 * <p>
 * 허용할 프론트 주소는 {@code application.yaml}의 {@code cors.allowed-origins}에 둔다.
 * 운영에서는 환경 변수 {@code CORS_ALLOWED_ORIGINS}로 덮어쓴다. 주소가 바뀌면 코드가 아니라 설정값을 고친다.
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    /** 요청을 허용할 프론트 주소 목록 */
    private final String[] allowedOrigins;

    /**
     * 설정 파일에서 허용 주소 목록(쉼표 구분)을 읽어 온다.
     */
    public WebConfig(@Value("${cors.allowed-origins}") String[] allowedOrigins) {
        this.allowedOrigins = allowedOrigins;
    }

    /**
     * 모든 경로에 대해 허용 주소에서 오는 요청을 받아들인다.
     * 로그인이 없어 쿠키를 주고받지 않으므로 자격 증명(credentials)은 허용하지 않는다.
     */
    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**")
                .allowedOrigins(allowedOrigins)
                .allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .maxAge(3600);
    }
}
