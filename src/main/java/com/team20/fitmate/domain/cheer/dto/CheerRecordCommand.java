package com.team20.fitmate.domain.cheer.dto;

import java.time.Instant;

/**
 * 저장할 응원 이벤트 하나. 실시간 계층이 대상에게 전달한 뒤 {@code CheerRecorder}로 넘긴다.
 * <p>
 * ERD의 {@code CHEER} 테이블(room_id, sender_id, receiver_id, reaction, created_at)과 같은 항목이다.
 *
 * @param roomId     응원이 오간 운동방 식별값
 * @param senderId   보낸 참가자 식별값
 * @param receiverId 받은 참가자 식별값
 * @param reaction   리액션 코드. 영문 대문자·숫자·밑줄 1~30자 (예: FIRE)
 * @param occurredAt 서버가 응원을 전달한 시각(UTC). 실시간 메시지의 sentAt과 같다
 */
public record CheerRecordCommand(
        Long roomId,
        Long senderId,
        Long receiverId,
        String reaction,
        Instant occurredAt
) {
}
