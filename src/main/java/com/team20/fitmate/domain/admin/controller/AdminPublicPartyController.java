package com.team20.fitmate.domain.admin.controller;

import com.team20.fitmate.domain.party.dto.PartyModeRequest;
import com.team20.fitmate.domain.party.dto.PartyScheduleRequest;
import com.team20.fitmate.domain.party.dto.PublicPartyResponse;
import com.team20.fitmate.domain.party.service.PublicPartyService;
import com.team20.fitmate.global.config.SwaggerConfig;
import com.team20.fitmate.global.rsdata.RsData;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 운영 화면(/admin)에서 공개 파티(선셋 에어로빅)를 바로 열고 닫거나 운영 시각을 바꾸는 API (이슈 #37).
 * <p>
 * 대회 시연 시간이 설정된 운영 시각과 다를 때 쓴다. 운영 키 확인은 {@code AdminKeyInterceptor}가 한다.
 * 응답은 {@code GET /api/public-parties/current}와 같은 형식이다.
 */
@Tag(name = "운영", description = "시연 운영 화면(/admin)용 API. X-Admin-Key 헤더 필요")
@SecurityRequirement(name = SwaggerConfig.ADMIN_KEY_SCHEME)
@RestController
@RequestMapping("/api/admin/public-party")
@RequiredArgsConstructor
public class AdminPublicPartyController {

    private final PublicPartyService publicPartyService;

    /**
     * 공개 파티 운영 모드와 운영 시각을 조회한다.
     */
    @Operation(summary = "공개 파티 운영 상태 조회",
            description = "GET /api/public-parties/current와 같은 응답이다. mode·openTime·durationMinutes로 운영 화면을 채운다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "200-1 조회 성공"),
            @ApiResponse(responseCode = "401", description = "401-1 운영 키 누락 또는 불일치"),
            @ApiResponse(responseCode = "403", description = "403-1 서버에 운영 키가 설정되지 않음")
    })
    @GetMapping
    public RsData<PublicPartyResponse> get() {
        return RsData.ok("공개 파티 운영 상태를 조회했습니다.", publicPartyService.getCurrent());
    }

    /**
     * 공개 파티 운영 모드를 바꾼다.
     */
    @Operation(summary = "공개 파티 운영 모드 변경",
            description = "AUTO: 운영 시각대로 / OPEN: 시각과 상관없이 바로 열고 바꿀 때까지 유지(closesAt null) / "
                    + "CLOSED: 시각과 상관없이 닫음(opensAt은 다음 운영 시각, 지금이 운영 시각 안이면 그다음 날). "
                    + "닫아도 들어와 있는 참가자를 내보내지 않고 진행 중인 운동 기록은 끝까지 저장된다. "
                    + "모드는 서버를 다시 시작하면 AUTO로 돌아간다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "200-1 변경 성공"),
            @ApiResponse(responseCode = "400", description = "400-1 mode 누락, 400-2 mode 값 오류"),
            @ApiResponse(responseCode = "401", description = "401-1 운영 키 누락 또는 불일치"),
            @ApiResponse(responseCode = "403", description = "403-1 서버에 운영 키가 설정되지 않음")
    })
    @PutMapping
    public RsData<PublicPartyResponse> changeMode(@Valid @RequestBody PartyModeRequest request) {
        return RsData.ok("공개 파티 운영 모드를 바꿨습니다.", publicPartyService.changeMode(request.mode()));
    }

    /**
     * 공개 파티 운영 시각을 바꾼다.
     */
    @Operation(summary = "공개 파티 운영 시각 변경",
            description = "매일 여는 시각(한국 시간 HH:mm)과 운영 시간(10~720분)을 바꾼다. 자정을 넘겨도 된다"
                    + "(23:30부터 90분이면 다음 날 01:00에 닫힘). DB에 저장해 재배포 뒤에도 유지된다. 운영 모드는 바꾸지 않는다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "200-1 변경 성공"),
            @ApiResponse(responseCode = "400", description = "400-1 openTime 형식 오류·durationMinutes 범위 밖, 400-2 본문 오류"),
            @ApiResponse(responseCode = "401", description = "401-1 운영 키 누락 또는 불일치"),
            @ApiResponse(responseCode = "403", description = "403-1 서버에 운영 키가 설정되지 않음")
    })
    @PutMapping("/schedule")
    public RsData<PublicPartyResponse> changeSchedule(@Valid @RequestBody PartyScheduleRequest request) {
        return RsData.ok("공개 파티 운영 시각을 바꿨습니다.", publicPartyService.changeSchedule(request));
    }
}
