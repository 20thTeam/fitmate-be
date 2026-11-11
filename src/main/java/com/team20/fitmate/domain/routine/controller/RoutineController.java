package com.team20.fitmate.domain.routine.controller;

import com.team20.fitmate.domain.participant.controller.ParticipantController;
import com.team20.fitmate.domain.routine.dto.NextRoundCreateRequest;
import com.team20.fitmate.domain.routine.dto.RoutineCreateRequest;
import com.team20.fitmate.domain.routine.dto.RoutineResponse;
import com.team20.fitmate.domain.routine.service.RoutineService;
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
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 루틴 API. 목표로 첫 회차 루틴을, 운동 기록으로 다음 회차 루틴을 추천받고, 루틴 확인 화면에서 조회한다.
 */
@Tag(name = "루틴", description = "첫 회차·다음 회차 루틴 추천과 조회")
@RestController
@RequestMapping("/api/routines")
@RequiredArgsConstructor
public class RoutineController {

    private final RoutineService routineService;

    /**
     * 목표로 첫 회차 루틴을 추천받아 저장한다.
     */
    @Operation(summary = "첫 회차 루틴 추천",
            description = "선택한 목표로 운동 2~3개와 순서·수행량·세트를 추천받아 참가자에 연결해 저장한다. "
                    + "추천 불가면 status가 UNAVAILABLE이고 exercises는 빈 목록이다.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "201-1 추천 결과 저장 (완료 또는 불가)"),
            @ApiResponse(responseCode = "400", description = "400-1 목표 누락, 400-2 없는 목표 값, 400-3 토큰 헤더 누락"),
            @ApiResponse(responseCode = "404", description = "404-2 토큰에 해당하는 참가자 없음")
    })
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public RsData<RoutineResponse> createFirstRound(
            @Parameter(description = "참가자 생성 때 받은 재방문 토큰", required = true)
            @RequestHeader(ParticipantController.TOKEN_HEADER) String clientToken,
            @Valid @RequestBody RoutineCreateRequest request) {
        return RsData.created("루틴 추천 결과입니다.", routineService.createFirstRound(clientToken, request.goal()));
    }

    /**
     * 종료된 운동 기록으로 다음 회차 루틴을 추천받는다.
     */
    @Operation(summary = "다음 회차 루틴 추천",
            description = "종료된 본인 운동 기록을 평가해 다음 회차 루틴과 운동별 변경 이유를 돌려준다. "
                    + "운동 구성·순서·세트는 그대로이고 수행량만 바뀐다. "
                    + "adjustmentType은 UP(상향), KEEP(유지), UNAVAILABLE(기록이 없어 분석 불가, 올리지 않음)이다. "
                    + "같은 운동 기록으로 다시 요청하면 처음 만든 추천을 그대로 돌려준다. "
                    + "판정 기준·증가 폭은 시연용 임시 규칙이다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "200-1 추천 결과 (새로 만들었거나 이미 만든 추천)"),
            @ApiResponse(responseCode = "400", description = "400-1 workoutId 누락, 400-2 본문 오류, 400-3 토큰 헤더 누락"),
            @ApiResponse(responseCode = "404", description = "404-2 참가자 없음, 404-5 운동 기록 없음(다른 참가자의 기록 포함)"),
            @ApiResponse(responseCode = "409", description = "409-3 아직 종료되지 않은 운동 기록")
    })
    @PostMapping("/next-round")
    public RsData<RoutineResponse> createNextRound(
            @Parameter(description = "참가자 생성 때 받은 재방문 토큰", required = true)
            @RequestHeader(ParticipantController.TOKEN_HEADER) String clientToken,
            @Valid @RequestBody NextRoundCreateRequest request) {
        return RsData.ok("다음 회차 루틴 추천 결과입니다.",
                routineService.createNextRound(clientToken, request.workoutId()));
    }

    /**
     * 본인 루틴을 조회한다.
     */
    @Operation(summary = "루틴 조회", description = "루틴 확인 화면용. 본인 루틴만 조회할 수 있다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "200-1 조회 성공"),
            @ApiResponse(responseCode = "400", description = "400-3 토큰 헤더 누락 또는 routineId 형식 오류"),
            @ApiResponse(responseCode = "404", description = "404-2 참가자 없음, 404-3 루틴 없음(다른 참가자의 루틴 포함)")
    })
    @GetMapping("/{routineId}")
    public RsData<RoutineResponse> get(
            @Parameter(description = "참가자 생성 때 받은 재방문 토큰", required = true)
            @RequestHeader(ParticipantController.TOKEN_HEADER) String clientToken,
            @Parameter(description = "루틴 식별값", required = true)
            @PathVariable("routineId") Long routineId) {
        return RsData.ok("루틴을 조회했습니다.", routineService.get(clientToken, routineId));
    }
}
