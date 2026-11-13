package com.team20.fitmate.domain.party.dto;

import com.team20.fitmate.domain.party.entity.PartyMode;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.OffsetDateTime;

/**
 * 공개 파티 오픈 상태 응답. 홈의 선셋 카드가 이 값으로 열림·닫힘과 입장 버튼을 정한다.
 * 운영 화면의 모드·운영 시각 조회·변경도 같은 형식으로 응답한다(이슈 #37).
 *
 * @param partyId          공개 파티 식별값. 실시간 JOIN의 {@code partyId}로 보낸다
 * @param name             카드에 보여줄 이름
 * @param mode             운영 모드. AUTO면 운영 시각대로, OPEN·CLOSED면 운영자가 정한 대로다
 * @param status           지금 열려 있으면 OPEN, 아니면 CLOSED
 * @param opensAt          열려 있으면 이번에 연 시각, 닫혀 있으면 다음에 여는 시각
 * @param closesAt         opensAt 구간이 닫히는 시각. 운영자가 연(mode OPEN) 동안은 닫는 시각이 없어 null
 * @param participantCount 지금 실시간 방에 들어와 있는 참가자 수(재접속 대기 포함)
 * @param openTime         매일 여는 시각(HH:mm)
 * @param durationMinutes  열어 두는 시간(분)
 */
@Schema(description = "공개 파티 오픈 상태")
public record PublicPartyResponse(
        @Schema(description = "공개 파티 식별값. 실시간 JOIN의 partyId로 보낸다", example = "1")
        Long partyId,
        @Schema(description = "카드에 보여줄 이름", example = "선셋 에어로빅")
        String name,
        @Schema(description = "운영 모드. AUTO: 운영 시각대로 / OPEN: 운영자가 엶 / CLOSED: 운영자가 닫음", example = "AUTO")
        PartyMode mode,
        @Schema(description = "OPEN: 입장 가능 / CLOSED: 입장 불가", example = "OPEN")
        PartyStatus status,
        @Schema(description = "열려 있으면 이번에 연 시각, 닫혀 있으면 다음에 여는 시각 (기준 시간대 오프셋 포함)",
                example = "2026-11-11T22:00:00+09:00")
        OffsetDateTime opensAt,
        @Schema(description = "opensAt 구간이 닫히는 시각. mode가 OPEN이면 운영자가 닫을 때까지라 null",
                example = "2026-11-11T23:00:00+09:00", nullable = true)
        OffsetDateTime closesAt,
        @Schema(description = "지금 실시간 방에 들어와 있는 참가자 수(재접속 대기 포함)", example = "3")
        int participantCount,
        @Schema(description = "매일 여는 시각 (기준 시간대 HH:mm)", example = "22:00")
        String openTime,
        @Schema(description = "열어 두는 시간(분)", example = "60")
        int durationMinutes
) {

    /**
     * 공개 파티 오픈 상태.
     */
    public enum PartyStatus {

        /** 지금 입장할 수 있음 */
        OPEN,

        /** 지금 입장할 수 없음. 홈 카드는 참여 중처럼 보이면 안 된다 */
        CLOSED
    }
}
