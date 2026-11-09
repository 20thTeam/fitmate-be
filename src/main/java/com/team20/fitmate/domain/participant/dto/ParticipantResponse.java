package com.team20.fitmate.domain.participant.dto;

import com.team20.fitmate.domain.participant.entity.Participant;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 참가자 조회 응답. 홈 상단 닉네임·캐릭터 표시용이며 재방문 토큰은 담지 않는다.
 *
 * @param participantId 임시 식별값
 * @param nickname      닉네임
 * @param characterCode 캐릭터 코드
 */
public record ParticipantResponse(

        @Schema(description = "참가자 임시 식별값", example = "1")
        Long participantId,

        @Schema(description = "닉네임", example = "아현")
        String nickname,

        @Schema(description = "캐릭터 코드", example = "CHARACTER_01")
        String characterCode
) {

    /**
     * 참가자 엔티티를 조회 응답으로 바꾼다.
     */
    public static ParticipantResponse from(Participant participant) {
        return new ParticipantResponse(
                participant.getId(),
                participant.getNickname(),
                participant.getCharacterCode()
        );
    }
}
