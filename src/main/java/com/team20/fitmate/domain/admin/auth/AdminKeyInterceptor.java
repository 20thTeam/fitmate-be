package com.team20.fitmate.domain.admin.auth;

import com.team20.fitmate.global.exception.CustomException;
import com.team20.fitmate.global.exception.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.cors.CorsUtils;
import org.springframework.web.servlet.HandlerInterceptor;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * 운영 API({@code /api/admin/**}) 요청의 운영 키를 확인한다.
 * <p>
 * 요청 헤더 {@value #ADMIN_KEY_HEADER}를 서버 설정값과 비교한다. 운영자가 화면에 직접 입력한 값을 프론트가 헤더로 보낸다.
 * <ul>
 *     <li>서버에 키가 없으면 403-1 — 키 없이 운영 API가 열리지 않게 한다</li>
 *     <li>헤더가 없거나 값이 다르면 401-1</li>
 * </ul>
 * 던진 예외는 {@code GlobalExceptionHandler}가 다른 API와 같은 {@code RsData} 형식으로 응답한다.
 */
@RequiredArgsConstructor
public class AdminKeyInterceptor implements HandlerInterceptor {

    /** 운영 키 요청 헤더 이름 */
    public static final String ADMIN_KEY_HEADER = "X-Admin-Key";

    private final AdminProperties properties;

    /**
     * 컨트롤러 실행 전에 운영 키를 확인한다. 맞지 않으면 예외를 던져 컨트롤러까지 가지 않게 한다.
     */
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        // 브라우저의 CORS 사전 요청(OPTIONS)은 사용자 헤더를 싣지 않으므로 키 확인 없이 통과시킨다
        if (CorsUtils.isPreFlightRequest(request)) {
            return true;
        }
        if (!properties.isEnabled()) {
            throw new CustomException(ErrorCode.ADMIN_DISABLED);
        }
        String given = request.getHeader(ADMIN_KEY_HEADER);
        if (given == null || !matches(given, properties.key())) {
            throw new CustomException(ErrorCode.ADMIN_KEY_INVALID);
        }
        return true;
    }

    /**
     * 두 키가 같은지 비교한다. 응답 시간 차이로 키를 추측하지 못하게 일정 시간 비교를 쓴다.
     */
    private static boolean matches(String given, String expected) {
        return MessageDigest.isEqual(given.getBytes(StandardCharsets.UTF_8), expected.getBytes(StandardCharsets.UTF_8));
    }
}
