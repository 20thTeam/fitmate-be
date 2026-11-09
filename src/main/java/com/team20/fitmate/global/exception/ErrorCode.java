package com.team20.fitmate.global.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

/**
 * 서비스 전체에서 쓰는 오류 코드 목록.
 * <p>
 * 각 오류는 HTTP 상태, {@code RsData}의 resultCode, 프론트에 보여줄 메시지를 가진다.
 * 도메인별 오류(참가자·루틴 등)는 담당 슬라이스에서 이 enum에 추가한다.
 */
@Getter
@RequiredArgsConstructor
public enum ErrorCode {

    // ===== 공통 =====

    /** 요청 값 검증 실패 (필수 값 누락, 형식 오류 등) */
    INVALID_INPUT(HttpStatus.BAD_REQUEST, "400-1", "요청 값이 올바르지 않습니다."),

    /** 요청 본문(JSON)을 읽을 수 없음 */
    INVALID_REQUEST_BODY(HttpStatus.BAD_REQUEST, "400-2", "요청 본문을 읽을 수 없습니다."),

    /** 필수 요청 파라미터 누락 또는 파라미터 타입 불일치 */
    INVALID_PARAMETER(HttpStatus.BAD_REQUEST, "400-3", "요청 파라미터가 올바르지 않습니다."),

    /** 존재하지 않는 주소로 요청 */
    NOT_FOUND(HttpStatus.NOT_FOUND, "404-1", "요청한 경로를 찾을 수 없습니다."),

    /** 지원하지 않는 HTTP 메서드로 요청 */
    METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED, "405-1", "지원하지 않는 요청 방식입니다."),

    /** 처리하지 못한 서버 내부 오류. 내부 메시지는 응답에 노출하지 않는다 */
    INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "500-1", "서버 오류가 발생했습니다."),

    // ===== 참가자 =====

    /** 재방문 토큰에 해당하는 참가자가 없음 */
    PARTICIPANT_NOT_FOUND(HttpStatus.NOT_FOUND, "404-2", "참가자를 찾을 수 없습니다.");

    /** 응답 HTTP 상태 */
    private final HttpStatus status;

    /** RsData의 resultCode 값 */
    private final String resultCode;

    /** 프론트에 보여줄 메시지 */
    private final String msg;
}
