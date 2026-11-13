package com.team20.fitmate.domain.party.controller;

import com.team20.fitmate.domain.party.dto.PublicPartyResponse;
import com.team20.fitmate.domain.party.service.PublicPartyService;
import com.team20.fitmate.global.rsdata.RsData;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 공개 파티(공개 선셋 에어로빅, P1) API. 홈의 선셋 카드가 오픈 상태를 조회한다.
 * 입장 자체는 REST가 아니라 실시간 연결의 JOIN({@code partyId})으로 한다.
 */
@Tag(name = "공개 파티", description = "공개 선셋 에어로빅 오픈 상태 조회 (이슈 #11, P1)")
@RestController
@RequestMapping("/api/public-parties")
@RequiredArgsConstructor
public class PublicPartyController {

    private final PublicPartyService publicPartyService;

    /**
     * 지금 공개 파티가 열려 있는지와 시간 구간, 현재 인원을 조회한다.
     */
    @Operation(summary = "공개 파티 오픈 상태 조회",
            description = "운영 모드(AUTO·OPEN·CLOSED)와 운영 시각 기준으로 지금 열려 있는지 알려 준다. "
                    + "닫혀 있으면 다음에 여는 시각을 준다. 운영자가 연(mode OPEN) 동안은 closesAt이 null이다. "
                    + "로그인·친구 파티 소속과 관계없이 누구나 조회한다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "200-1 조회 성공 (status가 OPEN 또는 CLOSED)")
    })
    @GetMapping("/current")
    public RsData<PublicPartyResponse> getCurrent() {
        return RsData.ok("공개 파티 상태를 조회했습니다.", publicPartyService.getCurrent());
    }
}
