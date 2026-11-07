package com.team20.fitmate.global.exception;

import com.team20.fitmate.global.rsdata.RsData;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.List;

/**
 * 컨트롤러에서 올라온 예외를 {@link RsData} 응답으로 바꾸는 전역 예외 처리기.
 * <p>
 * 내부 예외 메시지(스택, SQL, 클래스명 등)는 응답에 그대로 노출하지 않고
 * {@link ErrorCode}에 정해 둔 메시지만 내려준다. 원인은 서버 로그로 남긴다.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * 서비스 로직에서 던진 {@link CustomException} 처리.
     */
    @ExceptionHandler(CustomException.class)
    public ResponseEntity<RsData<Void>> handleCustomException(CustomException e) {
        log.warn("CustomException: {}", e.getErrorCode());
        return toResponse(e.getErrorCode());
    }

    /**
     * {@code @Valid} 요청 본문 검증 실패 처리.
     * data에 어떤 필드가 왜 틀렸는지 목록으로 담아 프론트가 입력 칸별 안내를 할 수 있게 한다.
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<RsData<List<FieldErrorResponse>>> handleValidation(MethodArgumentNotValidException e) {
        List<FieldErrorResponse> fieldErrors = e.getBindingResult().getFieldErrors().stream()
                .map(error -> new FieldErrorResponse(error.getField(), error.getDefaultMessage()))
                .toList();

        ErrorCode errorCode = ErrorCode.INVALID_INPUT;
        return ResponseEntity
                .status(errorCode.getStatus())
                .body(RsData.of(errorCode.getResultCode(), errorCode.getMsg(), fieldErrors));
    }

    /**
     * JSON 형식 오류 등으로 요청 본문을 읽지 못한 경우 처리.
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<RsData<Void>> handleNotReadable(HttpMessageNotReadableException e) {
        log.warn("HttpMessageNotReadableException: {}", e.getMessage());
        return toResponse(ErrorCode.INVALID_REQUEST_BODY);
    }

    /**
     * 필수 요청 파라미터 누락, 파라미터 타입 불일치(예: 숫자 자리에 문자) 처리.
     */
    @ExceptionHandler({MissingServletRequestParameterException.class, MethodArgumentTypeMismatchException.class})
    public ResponseEntity<RsData<Void>> handleInvalidParameter(Exception e) {
        log.warn("{}: {}", e.getClass().getSimpleName(), e.getMessage());
        return toResponse(ErrorCode.INVALID_PARAMETER);
    }

    /**
     * 존재하지 않는 경로로 요청한 경우 처리.
     */
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<RsData<Void>> handleNotFound(NoResourceFoundException e) {
        return toResponse(ErrorCode.NOT_FOUND);
    }

    /**
     * 지원하지 않는 HTTP 메서드로 요청한 경우 처리.
     */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<RsData<Void>> handleMethodNotAllowed(HttpRequestMethodNotSupportedException e) {
        return toResponse(ErrorCode.METHOD_NOT_ALLOWED);
    }

    /**
     * 위에서 처리하지 못한 모든 예외. 원인은 로그로만 남기고 응답에는 공통 메시지만 내려준다.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<RsData<Void>> handleException(Exception e) {
        log.error("처리하지 못한 예외", e);
        return toResponse(ErrorCode.INTERNAL_SERVER_ERROR);
    }

    /**
     * 오류 코드를 HTTP 상태와 RsData 본문으로 바꾼다.
     */
    private ResponseEntity<RsData<Void>> toResponse(ErrorCode errorCode) {
        return ResponseEntity
                .status(errorCode.getStatus())
                .body(RsData.of(errorCode.getResultCode(), errorCode.getMsg()));
    }

    /**
     * 검증 실패한 필드 하나의 정보.
     *
     * @param field  검증에 실패한 필드 이름
     * @param reason 실패 이유 (검증 어노테이션의 message)
     */
    public record FieldErrorResponse(String field, String reason) {
    }
}
