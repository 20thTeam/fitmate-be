package com.team20.fitmate.global.config;

import com.team20.fitmate.domain.admin.auth.AdminKeyInterceptor;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityScheme;
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

    /** 운영 키 인증 방식 이름. 운영 API 컨트롤러의 {@code @SecurityRequirement}에서 이 이름을 쓴다 */
    public static final String ADMIN_KEY_SCHEME = "adminKey";

    /**
     * 문서 상단에 보이는 API 기본 정보(제목·설명·버전)를 등록한다.
     */
    @Bean
    public OpenAPI fitmateOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("FITMATE API")
                        .description("친구와 함께하는 AI 소셜 홈트레이닝 서비스 FITMATE 백엔드 API 문서")
                        .version("v0.0.1"))
                // 운영 API는 X-Admin-Key 헤더로 인증한다. Swagger 상단 Authorize 버튼에서 키를 넣고 시험할 수 있다
                .components(new Components()
                        .addSecuritySchemes(ADMIN_KEY_SCHEME, new SecurityScheme()
                                .type(SecurityScheme.Type.APIKEY)
                                .in(SecurityScheme.In.HEADER)
                                .name(AdminKeyInterceptor.ADMIN_KEY_HEADER)
                                .description("운영 API 키. 서버 환경 변수 ADMIN_KEY와 같은 값")));
    }
}
