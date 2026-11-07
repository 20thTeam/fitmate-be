package com.team20.fitmate.global.exception;

import lombok.Getter;

/**
 * 서비스 로직에서 의도적으로 던지는 예외.
 * <p>
 * 사용 예) {@code throw new CustomException(ErrorCode.INVALID_INPUT);}
 * {@link GlobalExceptionHandler}가 받아서 {@link ErrorCode}에 맞는 {@code RsData} 응답으로 바꾼다.
 */
@Getter
public class CustomException extends RuntimeException {

    /** 이 예외가 나타내는 오류 코드 */
    private final ErrorCode errorCode;

    /**
     * 오류 코드만으로 예외를 만든다. 예외 메시지는 오류 코드의 기본 메시지를 쓴다.
     */
    public CustomException(ErrorCode errorCode) {
        super(errorCode.getMsg());
        this.errorCode = errorCode;
    }
}
