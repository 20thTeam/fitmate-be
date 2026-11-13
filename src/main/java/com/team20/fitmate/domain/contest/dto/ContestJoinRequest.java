package com.team20.fitmate.domain.contest.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 판 참가 요청. 로그인·참가자 토큰 없이 닉네임과 학교만 받는다.
 *
 * @param nickname 닉네임 (중복 허용)
 * @param schoolId 학교 식별값. 프론트가 가진 학교 목록의 값을 그대로 보낸다
 */
public record ContestJoinRequest(

        @Schema(description = "닉네임 (중복 허용, 1~20자)", example = "참가자1")
        @NotBlank(message = "닉네임을 입력해 주세요.")
        @Size(max = 20, message = "닉네임은 20자 이하로 입력해 주세요.")
        String nickname,

        @Schema(description = "학교 식별값. 서버는 그대로 저장해 묶기만 한다 "
                + "(현재 catholic, khu, kw, seoultech, skhu, shinhan, ptu, hufs)", example = "seoultech")
        @NotBlank(message = "학교를 선택해 주세요.")
        @Size(max = 30, message = "학교 식별값은 30자 이하여야 합니다.")
        String schoolId
) {
}
