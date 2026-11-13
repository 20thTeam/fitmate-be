package com.team20.fitmate.domain.party.dto;

import com.team20.fitmate.domain.party.entity.PartyMode;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

/**
 * 공개 파티 운영 모드 변경 요청.
 *
 * @param mode AUTO(운영 시각대로) / OPEN(바로 열기) / CLOSED(바로 닫기)
 */
public record PartyModeRequest(

        @Schema(description = "AUTO: 운영 시각대로 / OPEN: 시각과 상관없이 열기 / CLOSED: 시각과 상관없이 닫기", example = "OPEN")
        @NotNull(message = "운영 모드를 선택해 주세요.")
        PartyMode mode
) {
}
