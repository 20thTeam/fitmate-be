package com.team20.fitmate.domain.admin.controller;

import com.team20.fitmate.domain.admin.dto.AdminParticipantResponse;
import com.team20.fitmate.domain.admin.dto.AdminRoomResponse;
import com.team20.fitmate.domain.admin.dto.AdminWorkoutResponse;
import com.team20.fitmate.domain.admin.service.AdminService;
import com.team20.fitmate.global.config.SwaggerConfig;
import com.team20.fitmate.global.rsdata.RsData;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 운영 API. 시연 운영 화면(/admin)이 방 접속자와 전체 참가자·운동 기록을 보는 데 쓴다.
 * <p>
 * 모든 요청에 운영 키 헤더({@code X-Admin-Key})가 필요하다. 확인은 {@code AdminKeyInterceptor}가 한다.
 */
@Tag(name = "운영", description = "시연 운영 화면(/admin)용 API. X-Admin-Key 헤더 필요")
@SecurityRequirement(name = SwaggerConfig.ADMIN_KEY_SCHEME)
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminController {

    private final AdminService adminService;

    /**
     * 운동방별 입장 참가자와 웹소켓 연결 여부를 조회한다.
     */
    @Operation(summary = "방 접속자 현황",
            description = "GET /api/rooms 정보에 REST 입장한 참가자와 지금 웹소켓 연결 여부(connected)를 더한다. "
                    + "연결 불안정(재접속 대기)이면 connected는 false다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "200-1 조회 성공"),
            @ApiResponse(responseCode = "401", description = "401-1 운영 키 누락 또는 불일치"),
            @ApiResponse(responseCode = "403", description = "403-1 서버에 운영 키가 설정되지 않음")
    })
    @GetMapping("/rooms")
    public RsData<List<AdminRoomResponse>> getRooms() {
        return RsData.ok("운동방 접속자 현황을 조회했습니다.", adminService.getRooms());
    }

    /**
     * 전체 참가자를 조회한다.
     */
    @Operation(summary = "전체 참가자 목록",
            description = "최근 만든 순. 생성 시각과 종료(확정)된 운동 기록 수를 담는다. 재방문 토큰(clientToken)은 담지 않는다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "200-1 조회 성공"),
            @ApiResponse(responseCode = "401", description = "401-1 운영 키 누락 또는 불일치"),
            @ApiResponse(responseCode = "403", description = "403-1 서버에 운영 키가 설정되지 않음")
    })
    @GetMapping("/participants")
    public RsData<List<AdminParticipantResponse>> getParticipants() {
        return RsData.ok("전체 참가자를 조회했습니다.", adminService.getParticipants());
    }

    /**
     * 전체 운동 기록을 조회한다.
     */
    @Operation(summary = "전체 운동 기록 목록",
            description = "최근 시작 순. 진행 중(IN_PROGRESS) 기록도 담으며 이때 finishedAt·elapsedSeconds·totalValidCount·earnedXp는 null이다. "
                    + "측정하지 못한 기록의 totalValidCount는 0이 아니라 null이다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "200-1 조회 성공"),
            @ApiResponse(responseCode = "400", description = "400-3 participantId 형식 오류"),
            @ApiResponse(responseCode = "401", description = "401-1 운영 키 누락 또는 불일치"),
            @ApiResponse(responseCode = "403", description = "403-1 서버에 운영 키가 설정되지 않음")
    })
    @GetMapping("/workouts")
    public RsData<List<AdminWorkoutResponse>> getWorkouts(
            @Parameter(description = "이 참가자 기록만 본다. 없으면 전체. 없는 참가자면 빈 목록")
            @RequestParam(name = "participantId", required = false) Long participantId) {
        return RsData.ok("운동 기록을 조회했습니다.", adminService.getWorkouts(participantId));
    }
}
