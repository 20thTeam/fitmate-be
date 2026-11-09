package com.team20.fitmate.realtime.message;

/**
 * PARTICIPANT_LEFT 내용.
 *
 * @param reason 퇴장 이유
 */
public record LeftPayload(LeaveReason reason) {

    /**
     * 퇴장 이유.
     */
    public enum LeaveReason {

        /** 참가자가 LEAVE를 보내 직접 나감 */
        LEFT,

        /** 연결이 끊긴 뒤 재접속 대기 시간 안에 돌아오지 않음 */
        TIMEOUT
    }
}
