package com.team20.fitmate.realtime.message;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 실시간 연결에서 쓰는 오류 코드.
 * <p>
 * REST의 {@code ErrorCode}와 따로 둔다. 실시간 오류는 HTTP 상태가 없고, 연결을 닫을지 여부가 핵심이기 때문이다.
 * {@code closesConnection}이 true인 오류는 ERROR를 보낸 뒤 서버가 연결을 닫는다.
 */
@Getter
@RequiredArgsConstructor
public enum RealtimeErrorCode {

    /** JSON이 아니거나, 종류를 모르거나, 필수 값이 빠졌거나 형식이 틀림 */
    INVALID_MESSAGE("메시지 형식이 올바르지 않습니다.", false),

    /** JOIN 전에 다른 메시지를 보냄 */
    JOIN_REQUIRED("먼저 운동방에 입장해 주세요.", false),

    /** 이미 입장한 연결에서 JOIN을 다시 보냄 */
    ALREADY_JOINED("이미 운동방에 입장했습니다.", false),

    /** 재방문 토큰에 해당하는 참가자가 없음 */
    PARTICIPANT_NOT_FOUND("참가자를 찾을 수 없습니다. 프로필을 다시 설정해 주세요.", true),

    /** 운동방이 없음 */
    ROOM_NOT_FOUND("운동방을 찾을 수 없습니다.", true),

    /** REST 입장(POST /api/rooms/{roomId}/enter)을 하지 않은 방에 실시간 연결을 시도함 */
    NOT_ENTERED_ROOM("운동방 입장을 먼저 완료해 주세요.", true),

    /** 정원이 찼음 */
    ROOM_FULL("운동방 정원이 가득 찼습니다.", true),

    /** 같은 참가자가 다른 창(연결)으로 다시 입장해 이 연결이 밀려남 */
    SESSION_REPLACED("다른 창에서 같은 참가자로 입장해 이 연결을 닫습니다.", true),

    /** 연결 후 정해진 시간 안에 JOIN을 보내지 않음 */
    JOIN_TIMEOUT("입장 요청이 없어 연결을 닫습니다.", true),

    /** 응원 대상이 같은 방에 없거나(퇴장·다른 방) 연결 불안정이라 전달하지 못함. 연결은 유지한다 */
    CHEER_TARGET_UNAVAILABLE("응원할 친구가 지금 연결되어 있지 않습니다.", false),

    /** 처리하지 못한 서버 오류. 내부 메시지는 노출하지 않는다 */
    INTERNAL_ERROR("서버 오류가 발생했습니다.", false);

    /** 사용자에게 보여줄 수 있는 안내 문구 */
    private final String message;

    /** ERROR를 보낸 뒤 연결을 닫는지 */
    private final boolean closesConnection;
}
