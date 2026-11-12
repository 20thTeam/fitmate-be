package com.team20.fitmate.domain.admin.dto;

import com.team20.fitmate.domain.participant.entity.Participant;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

/**
 * 운영 화면의 참가자 한 명. {@code ParticipantResponse}에 생성 시각과 확정된 운동 기록 수를 더했다.
 * <p>
 * 재방문 토큰({@code clientToken})은 담지 않는다. 토큰이 있으면 그 참가자인 척할 수 있기 때문이다.
 *
 * @param participantId 참가자 식별값
 * @param nickname      닉네임
 * @param characterCode 캐릭터 코드
 * @param roomId        마지막으로 입장한 운동방 식별값. 입장 전이면 null
 * @param createdAt     참가자를 만든 시각
 * @param workoutCount  종료(확정)된 운동 기록 수. 진행 중 기록은 세지 않는다
 */
public record AdminParticipantResponse(

        @Schema(description = "참가자 식별값", example = "12")
        Long participantId,

        @Schema(description = "닉네임", example = "민지")
        String nickname,

        @Schema(description = "캐릭터 코드", example = "CHARACTER_01")
        String characterCode,

        @Schema(description = "마지막으로 입장한 운동방 식별값. 입장 전이면 null", example = "1", nullable = true)
        Long roomId,

        @Schema(description = "참가자를 만든 시각", example = "2026-11-11T20:15:00")
        LocalDateTime createdAt,

        @Schema(description = "종료(확정)된 운동 기록 수. 진행 중 기록은 세지 않는다", example = "3")
        long workoutCount
) {

    /**
     * 참가자와 확정 운동 기록 수로 응답을 만든다. 운동방(LAZY) 식별값을 읽으므로 트랜잭션 안에서 부른다.
     */
    public static AdminParticipantResponse of(Participant participant, long workoutCount) {
        return new AdminParticipantResponse(
                participant.getId(),
                participant.getNickname(),
                participant.getCharacterCode(),
                participant.getRoom() == null ? null : participant.getRoom().getId(),
                participant.getCreatedAt(),
                workoutCount);
    }
}
