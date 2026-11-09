package com.team20.fitmate.domain.participant.dto;

import com.team20.fitmate.domain.participant.entity.Participant;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 참가자 생성 응답. 재방문 토큰은 이 응답에서만 내려준다.
 *
 * @param participantId 임시 식별값
 * @param clientToken   기기에 보관할 재방문 토큰. 이후 요청의 {@code X-Participant-Token} 헤더에 담는다
 * @param nickname      닉네임
 * @param characterCode 캐릭터 코드
 */
public record ParticipantCreateResponse(

        @Schema(description = "참가자 임시 식별값", example = "1")
        Long participantId,

        @Schema(description = "기기에 보관할 재방문 토큰", example = "3f1c2b6e-8a4d-4f7e-9b1a-2c5d7e9f0a12")
        String clientToken,

        @Schema(description = "닉네임", example = "아현")
        String nickname,

        @Schema(description = "캐릭터 코드", example = "CHARACTER_01")
        String characterCode
) {

    /**
     * 참가자 엔티티를 생성 응답으로 바꾼다.
     */
    public static ParticipantCreateResponse from(Participant participant) {
        return new ParticipantCreateResponse(
                participant.getId(),
                participant.getClientToken(),
                participant.getNickname(),
                participant.getCharacterCode()
        );
    }
}
