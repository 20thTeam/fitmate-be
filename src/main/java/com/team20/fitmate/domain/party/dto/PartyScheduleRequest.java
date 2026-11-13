package com.team20.fitmate.domain.party.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.time.LocalTime;

/**
 * 공개 파티 운영 시각 변경 요청. 매일 같은 시각 하나만 둔다(요일별 시각은 범위 밖).
 *
 * @param openTime        매일 여는 시각(한국 시간 HH:mm)
 * @param durationMinutes 열어 두는 시간(분, 10~720). 자정을 넘겨도 된다
 */
public record PartyScheduleRequest(

        @Schema(description = "매일 여는 시각 (한국 시간 HH:mm)", example = "21:30")
        @NotNull(message = "여는 시각을 입력해 주세요.")
        @Pattern(regexp = "^([01]\\d|2[0-3]):[0-5]\\d$", message = "여는 시각은 HH:mm 형식이어야 합니다.")
        String openTime,

        @Schema(description = "열어 두는 시간(분, 10~720). 자정을 넘겨도 된다", example = "90")
        @NotNull(message = "운영 시간을 입력해 주세요.")
        @Min(value = 10, message = "운영 시간은 10분 이상이어야 합니다.")
        @Max(value = 720, message = "운영 시간은 720분 이하여야 합니다.")
        Integer durationMinutes
) {

    /** 여는 시각 */
    public LocalTime openLocalTime() {
        return LocalTime.parse(openTime);
    }
}
