package com.team20.fitmate.domain.admin.auth;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 운영 API 설정({@code admin.*}).
 * <p>
 * 운영 키는 코드·프론트 환경 변수에 두지 않고 서버 환경 변수 {@code ADMIN_KEY}로만 받는다.
 * 값이 비어 있으면 운영 API 전체를 막는다(키 없이 열리는 일이 없게 기본값을 '꺼짐'으로 둔다).
 *
 * @param key 운영 키. 비어 있으면 운영 API 비활성
 */
@ConfigurationProperties(prefix = "admin")
public record AdminProperties(String key) {

    /**
     * 운영 키가 설정되어 운영 API를 쓸 수 있는지 확인한다.
     */
    public boolean isEnabled() {
        return key != null && !key.isBlank();
    }
}
