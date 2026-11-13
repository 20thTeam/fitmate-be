package com.team20.fitmate.domain.room.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 운동방 만들기 요청.
 *
 * @param name 운동방 이름. 다른 방과 같아도 된다
 */
public record RoomCreateRequest(

        @Schema(description = "운동방 이름 (1~20자, 중복 허용)", example = "민지의 운동방")
        @NotBlank(message = "운동방 이름을 입력해 주세요.")
        @Size(max = 20, message = "운동방 이름은 20자 이하로 입력해 주세요.")
        String name
) {
}
