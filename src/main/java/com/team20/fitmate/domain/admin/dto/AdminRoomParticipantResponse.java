package com.team20.fitmate.domain.admin.dto;

import com.team20.fitmate.domain.participant.entity.Participant;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 운동방에 입장한 참가자 한 명과 지금 웹소켓 연결 여부.
 *
 * @param participantId 참가자 식별값
 * @param nickname      닉네임
 * @param characterCode 캐릭터 코드
 * @param connected     지금 웹소켓(/ws)으로 연결돼 있는지. 연결 불안정(재접속 대기)이면 false
 */
public record AdminRoomParticipantResponse(

        @Schema(description = "참가자 식별값", example = "12")
        Long participantId,

        @Schema(description = "닉네임", example = "민지")
        String nickname,

        @Schema(description = "캐릭터 코드", example = "CHARACTER_01")
        String characterCode,

        @Schema(description = "지금 웹소켓(/ws)으로 연결돼 있는지. 연결 불안정(재접속 대기)이면 false", example = "true")
        boolean connected
) {

    /**
     * 참가자와 연결 여부로 응답을 만든다.
     */
    public static AdminRoomParticipantResponse of(Participant participant, boolean connected) {
        return new AdminRoomParticipantResponse(
                participant.getId(),
                participant.getNickname(),
                participant.getCharacterCode(),
                connected);
    }
}
