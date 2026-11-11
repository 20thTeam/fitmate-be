package com.team20.fitmate.domain.history.controller;

import com.team20.fitmate.domain.history.dto.HistoryResponse;
import com.team20.fitmate.domain.history.service.HistoryService;
import com.team20.fitmate.domain.participant.controller.ParticipantController;
import com.team20.fitmate.global.rsdata.RsData;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

/**
 * 성장 기록 API. 홈·결과 화면에서 내 회차별 기록과 누적량을 다시 본다.
 */
@Tag(name = "성장 기록", description = "내 회차별 기록과 누적량, 다음 추천 변화")
@RestController
@RequiredArgsConstructor
public class HistoryController {

    private final HistoryService historyService;

    /**
     * 토큰의 참가자 본인 성장 기록을 조회한다.
     */
    @Operation(summary = "내 성장 기록 조회",
            description = "종료한 운동만 최근 종료 순으로 돌려준다. 회차마다 운동별 수행 기록, 이 회차가 바뀐 이유, "
                    + "이 기록으로 만든 다음 회차 추천(없으면 null)을 담는다. "
                    + "기록이 없으면 workouts는 빈 목록이고 예시 수치는 없다. "
                    + "측정한 값이 없는 합계는 0이 아니라 null이다. 토큰의 본인 기록만 조회한다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "200-1 조회 성공 (기록이 없어도 200)"),
            @ApiResponse(responseCode = "400", description = "400-3 토큰 헤더 누락"),
            @ApiResponse(responseCode = "404", description = "404-2 토큰에 해당하는 참가자 없음")
    })
    @GetMapping("/api/participants/me/history")
    public RsData<HistoryResponse> getMyHistory(
            @Parameter(description = "참가자 생성 때 받은 재방문 토큰", required = true)
            @RequestHeader(ParticipantController.TOKEN_HEADER) String clientToken) {
        return RsData.ok("성장 기록을 조회했습니다.", historyService.getMyHistory(clientToken));
    }
}
