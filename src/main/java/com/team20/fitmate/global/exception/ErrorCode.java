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

    /** 수행 기록 값이 측정 상태·측정 방식 규칙에 맞지 않음 (예: 미측정인데 유효 횟수가 있음) */
    INVALID_EXERCISE_RECORD(HttpStatus.BAD_REQUEST, "400-4", "수행 기록 값이 올바르지 않습니다."),

    /** 응원 값이 올바르지 않음 (다른 운동방 참가자, 자기 자신, 빈 리액션 등) */
    INVALID_CHEER(HttpStatus.BAD_REQUEST, "400-5", "같은 운동방 친구에게만 응원할 수 있습니다."),

    /** 존재하지 않는 주소로 요청 */
    NOT_FOUND(HttpStatus.NOT_FOUND, "404-1", "요청한 경로를 찾을 수 없습니다."),

    /** 지원하지 않는 HTTP 메서드로 요청 */
    METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED, "405-1", "지원하지 않는 요청 방식입니다."),

    /** 처리하지 못한 서버 내부 오류. 내부 메시지는 응답에 노출하지 않는다 */
    INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "500-1", "서버 오류가 발생했습니다."),

    // ===== 운영 API =====

    /** 운영 키(X-Admin-Key)가 없거나 서버 설정값과 다름 */
    ADMIN_KEY_INVALID(HttpStatus.UNAUTHORIZED, "401-1", "운영 키가 올바르지 않습니다."),

    /** 서버에 운영 키가 설정되지 않아 운영 API를 쓸 수 없음 */
    ADMIN_DISABLED(HttpStatus.FORBIDDEN, "403-1", "운영 API가 꺼져 있습니다. 서버에 운영 키를 설정해야 합니다."),

    // ===== 참가자 =====

    /** 재방문 토큰에 해당하는 참가자가 없음 */
    PARTICIPANT_NOT_FOUND(HttpStatus.NOT_FOUND, "404-2", "참가자를 찾을 수 없습니다."),

    // ===== 루틴 =====

    /** 루틴이 없거나 본인 루틴이 아님 */
    ROUTINE_NOT_FOUND(HttpStatus.NOT_FOUND, "404-3", "루틴을 찾을 수 없습니다."),

    // ===== 운동방 =====

    /** 운동방이 없음 */
    ROOM_NOT_FOUND(HttpStatus.NOT_FOUND, "404-4", "운동방을 찾을 수 없습니다."),

    // ===== 운동 기록 =====

    /** 운동 기록이 없거나 본인 기록이 아님 */
    WORKOUT_NOT_FOUND(HttpStatus.NOT_FOUND, "404-5", "운동 기록을 찾을 수 없습니다."),

    // ===== 팀 =====

    /** 팀이 없음 */
    TEAM_NOT_FOUND(HttpStatus.NOT_FOUND, "404-6", "팀을 찾을 수 없습니다."),

    // ===== 운동 =====

    /** 운동 코드에 해당하는 운동이 없음 */
    EXERCISE_NOT_FOUND(HttpStatus.NOT_FOUND, "404-7", "운동을 찾을 수 없습니다."),

    // ===== 공개 파티 =====

    /** 공개 파티 식별값이 설정과 다름 */
    PUBLIC_PARTY_NOT_FOUND(HttpStatus.NOT_FOUND, "404-8", "공개 파티를 찾을 수 없습니다."),

    // ===== 운동 기록 (충돌) =====

    /** 이미 종료된 운동 기록에 다시 제출함 */
    WORKOUT_ALREADY_FINISHED(HttpStatus.CONFLICT, "409-1", "이미 종료된 운동 기록입니다."),

    /** 추천 불가 등 운동을 시작할 수 없는 루틴 */
    ROUTINE_NOT_STARTABLE(HttpStatus.CONFLICT, "409-2", "운동을 시작할 수 없는 루틴입니다."),

    /** 종료되지 않은 운동 기록으로 다음 회차를 추천받으려 함 */
    WORKOUT_NOT_FINISHED(HttpStatus.CONFLICT, "409-3", "아직 종료되지 않은 운동 기록입니다."),

    /** 공개 파티가 열려 있지 않은 시간에 입장하려 함 */
    PUBLIC_PARTY_CLOSED(HttpStatus.CONFLICT, "409-4", "공개 파티가 지금 열려 있지 않습니다."),

    // ===== 운동방 (충돌) =====

    /** 서버 전체 운동방 수가 상한(room.max-count)에 닿아 더 만들 수 없음 */
    ROOM_LIMIT_EXCEEDED(HttpStatus.CONFLICT, "409-5", "더 이상 운동방을 만들 수 없습니다.");

    /** 응답 HTTP 상태 */
    private final HttpStatus status;

    /** RsData의 resultCode 값 */
    private final String resultCode;

    /** 프론트에 보여줄 메시지 */
    private final String msg;
}
