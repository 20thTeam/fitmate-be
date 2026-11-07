package com.team20.fitmate.global.rsdata;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 모든 REST 응답을 감싸는 공통 응답 객체.
 * <p>
 * 형태: {@code { "resultCode": "200-1", "msg": "...", "data": {...} }}
 * <ul>
 *     <li>resultCode — "{HTTP 상태}-{세부 번호}" 형식의 결과 코드</li>
 *     <li>msg — 프론트에 보여줄 수 있는 결과 메시지</li>
 *     <li>data — 실제 응답 데이터. 없으면 null</li>
 * </ul>
 *
 * @param <T> 응답 데이터 타입
 */
@Getter
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class RsData<T> {

    /** 결과 코드. 예) "200-1", "400-1" */
    private final String resultCode;

    /** 결과 메시지 */
    private final String msg;

    /** 응답 데이터 */
    private final T data;

    /**
     * 결과 코드·메시지·데이터를 직접 지정해 응답을 만든다.
     */
    public static <T> RsData<T> of(String resultCode, String msg, T data) {
        return new RsData<>(resultCode, msg, data);
    }

    /**
     * 데이터 없이 결과 코드와 메시지만으로 응답을 만든다.
     */
    public static <T> RsData<T> of(String resultCode, String msg) {
        return new RsData<>(resultCode, msg, null);
    }

    /**
     * 조회·처리 성공(200) 응답을 만든다. 결과 코드는 "200-1".
     */
    public static <T> RsData<T> ok(String msg, T data) {
        return new RsData<>("200-1", msg, data);
    }

    /**
     * 생성 성공(201) 응답을 만든다. 결과 코드는 "201-1".
     */
    public static <T> RsData<T> created(String msg, T data) {
        return new RsData<>("201-1", msg, data);
    }
}
