package com.team20.fitmate.global.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Swagger(OpenAPI) 문서 설정.
 * <p>
 * 문서 페이지: {@code /swagger-ui/index.html}, 문서 JSON: {@code /v3/api-docs}
 * 각 엔드포인트에는 {@code @Tag}, {@code @Operation}, {@code @ApiResponses}를 붙여 이 문서에 노출한다.
 */
@Configuration
public class SwaggerConfig {

    /**
     * 문서 상단에 보이는 API 기본 정보(제목·설명·버전)를 등록한다.
     */
    @Bean
    public OpenAPI fitmateOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("FITMATE API")
                        .description("친구와 함께하는 AI 소셜 홈트레이닝 서비스 FITMATE 백엔드 API 문서")
                        .version("v0.0.1"));
    }
}
