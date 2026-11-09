package com.team20.fitmate.realtime.message;

/**
 * ERROR 내용. 내부 예외 메시지는 담지 않고, 정해 둔 코드와 안내 문구만 보낸다.
 *
 * @param code    오류 코드({@link RealtimeErrorCode} 이름)
 * @param message 사용자에게 보여줄 수 있는 안내 문구
 * @param closed  이 오류 뒤에 서버가 연결을 닫는지. true면 프론트는 재시도 수단을 보여준다
 */
public record ErrorPayload(String code, String message, boolean closed) {

    /**
     * 오류 코드로 내용을 만든다.
     */
    public static ErrorPayload from(RealtimeErrorCode errorCode) {
        return new ErrorPayload(errorCode.name(), errorCode.getMessage(), errorCode.isClosesConnection());
    }
}
