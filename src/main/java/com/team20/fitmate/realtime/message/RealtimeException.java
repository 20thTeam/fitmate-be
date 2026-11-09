package com.team20.fitmate.realtime.message;

import lombok.Getter;

/**
 * 실시간 처리 중 클라이언트에게 ERROR로 알려야 하는 실패.
 * 핸들러가 잡아서 {@link RealtimeErrorCode}에 맞는 ERROR 메시지로 바꾼다.
 */
@Getter
public class RealtimeException extends RuntimeException {

    /** 클라이언트에 보낼 오류 코드 */
    private final RealtimeErrorCode errorCode;

    public RealtimeException(RealtimeErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
    }
}
