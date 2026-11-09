package com.team20.fitmate.domain.xp.controller;

import com.team20.fitmate.domain.xp.dto.TeamXpResponse;
import com.team20.fitmate.domain.xp.service.TeamXpService;
import com.team20.fitmate.global.rsdata.RsData;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 팀 경험치 조회 API.
 */
@Tag(name = "팀 경험치", description = "팀 누적 경험치와 레벨 조회")
@RestController
@RequestMapping("/api/teams")
@RequiredArgsConstructor
public class TeamXpController {

    private final TeamXpService teamXpService;

    /**
     * 팀 누적 경험치를 조회한다.
     */
    @Operation(summary = "팀 누적 경험치 조회",
            description = "팀 누적 경험치와 레벨(Lv.1~5), 다음 레벨까지의 진행도 계산용 값을 조회한다. "
                    + "운동방의 팀 레벨 표시와 성장 기록에 쓴다. Lv.5는 최고 레벨이라 nextLevelXp가 null이다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "200-1 조회 성공"),
            @ApiResponse(responseCode = "400", description = "400-3 teamId 형식 오류"),
            @ApiResponse(responseCode = "404", description = "404-6 팀 없음")
    })
    @GetMapping("/{teamId}/xp")
    public RsData<TeamXpResponse> getTeamXp(
            @Parameter(description = "팀 식별값 (운동방 조회 응답의 teamId)", required = true)
            @PathVariable("teamId") Long teamId) {
        return RsData.ok("팀 경험치를 조회했습니다.", teamXpService.getTeamXp(teamId));
    }
}
