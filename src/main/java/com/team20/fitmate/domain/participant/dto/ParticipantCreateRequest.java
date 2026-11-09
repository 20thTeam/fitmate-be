package com.team20.fitmate.domain.participant.dto;

import com.team20.fitmate.domain.participant.entity.Participant;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 참가자 생성 요청. 프로필 설정 화면에서 정한 닉네임과 캐릭터를 받는다.
 * 길이 제한은 임시값이며 디자인·PM 확정 후 맞춘다.
 *
 * @param nickname      닉네임. 다른 참가자와 같아도 된다
 * @param characterCode 선택한 캐릭터 코드
 */
public record ParticipantCreateRequest(

        @Schema(description = "닉네임 (중복 허용, 1~20자)", example = "아현")
        @NotBlank(message = "닉네임을 입력해 주세요.")
        @Size(max = 20, message = "닉네임은 20자 이하로 입력해 주세요.")
        String nickname,

        @Schema(description = "캐릭터 코드", example = "CHARACTER_01")
        @NotBlank(message = "캐릭터를 선택해 주세요.")
        @Size(max = 30, message = "캐릭터 코드는 30자 이하여야 합니다.")
        String characterCode
) {

    /**
     * 요청 값으로 참가자 엔티티를 만든다. 앞뒤 공백은 지운다.
     */
    public Participant toEntity() {
        return Participant.create(nickname.strip(), characterCode.strip());
    }
}
