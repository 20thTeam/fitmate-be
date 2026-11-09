package com.team20.fitmate.domain.participant.controller;

import com.team20.fitmate.domain.participant.dto.ParticipantCreateRequest;
import com.team20.fitmate.domain.participant.dto.ParticipantCreateResponse;
import com.team20.fitmate.domain.participant.dto.ParticipantResponse;
import com.team20.fitmate.domain.participant.service.ParticipantService;
import com.team20.fitmate.global.rsdata.RsData;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 참가자 API. 프로필 설정에서 참가자를 발급하고, 홈에서 본인 정보를 조회한다.
 */
@Tag(name = "참가자", description = "임시 참가자 발급과 조회")
@RestController
@RequestMapping("/api/participants")
@RequiredArgsConstructor
public class ParticipantController {

    /** 본인 확인에 쓰는 재방문 토큰 헤더 이름 */
    public static final String TOKEN_HEADER = "X-Participant-Token";

    private final ParticipantService participantService;

    /**
     * 닉네임과 캐릭터를 받아 참가자를 발급한다.
     */
    @Operation(summary = "참가자 생성",
            description = "닉네임·캐릭터로 임시 참가자를 발급한다. 같은 닉네임이어도 다른 참가자로 발급된다. "
                    + "응답의 clientToken을 기기에 보관하고 이후 요청의 " + TOKEN_HEADER + " 헤더에 담는다.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "201-1 참가자 발급 성공"),
            @ApiResponse(responseCode = "400", description = "400-1 닉네임·캐릭터 누락 또는 길이 초과, 400-2 본문 형식 오류")
    })
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public RsData<ParticipantCreateResponse> create(@Valid @RequestBody ParticipantCreateRequest request) {
        return RsData.created("참가자가 생성되었습니다.", participantService.create(request));
    }

    /**
     * 재방문 토큰으로 본인 참가자 정보를 조회한다.
     */
    @Operation(summary = "내 참가자 정보 조회", description = "홈 상단 닉네임·캐릭터 표시용. 재방문 토큰으로 본인을 확인한다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "200-1 조회 성공"),
            @ApiResponse(responseCode = "400", description = "400-3 토큰 헤더 누락"),
            @ApiResponse(responseCode = "404", description = "404-2 토큰에 해당하는 참가자 없음")
    })
    @GetMapping("/me")
    public RsData<ParticipantResponse> getMe(
            @Parameter(description = "참가자 생성 때 받은 재방문 토큰", required = true)
            @RequestHeader(TOKEN_HEADER) String clientToken) {
        return RsData.ok("참가자 정보를 조회했습니다.", participantService.getByClientToken(clientToken));
    }
}
