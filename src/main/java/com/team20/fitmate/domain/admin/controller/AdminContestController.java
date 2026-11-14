package com.team20.fitmate.domain.admin.controller;

import com.team20.fitmate.domain.admin.service.AdminService;
import com.team20.fitmate.domain.contest.dto.ContestResetResponse;
import com.team20.fitmate.global.config.SwaggerConfig;
import com.team20.fitmate.global.rsdata.RsData;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 운영 화면(/admin)에서 학교 대항 스쿼트 챌린지 기록을 지우는 API (이슈 #41).
 * <p>
 * 리허설 기록을 지우고 시연을 시작할 때 쓴다. 운영 키 확인은 {@code AdminKeyInterceptor}가 한다
 * (키가 없거나 틀리면 401-1, 서버에 키가 없으면 403-1).
 */
@Tag(name = "운영", description = "시연 운영 화면(/admin)용 API. X-Admin-Key 헤더 필요")
@SecurityRequirement(name = SwaggerConfig.ADMIN_KEY_SCHEME)
@RestController
@RequestMapping("/api/admin/univ-contest")
@RequiredArgsConstructor
public class AdminContestController {

    private final AdminService adminService;

    /**
     * 챌린지 판·참가자·결과를 모두 지운다. 되돌릴 수 없다.
     */
    @Operation(summary = "챌린지 기록 초기화",
            description = "학교 대항 스쿼트 챌린지의 판·참가자·결과를 모두 지운다. 진행 중인 판도 지운다. "
                    + "지운 뒤 GET /api/univ-contest/standings는 gameCount 0과 빈 목록을 돌려준다. "
                    + "챌린지 판 웹소켓 연결은 1000 CONTEST_GAME_FINISHED로 닫는다(프론트가 다시 연결하지 않는다). "
                    + "친구 운동방·공개 파티 데이터와 연결은 건드리지 않는다. 되돌릴 수 없다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "200-1 초기화 성공 (지운 판·참가자 수)"),
            @ApiResponse(responseCode = "401", description = "401-1 운영 키 누락 또는 불일치 (아무것도 지우지 않음)"),
            @ApiResponse(responseCode = "403", description = "403-1 서버에 운영 키가 설정되지 않음 (아무것도 지우지 않음)")
    })
    @PostMapping("/reset")
    public RsData<ContestResetResponse> reset() {
        return RsData.ok("챌린지 기록을 초기화했습니다.", adminService.resetContest());
    }
}
