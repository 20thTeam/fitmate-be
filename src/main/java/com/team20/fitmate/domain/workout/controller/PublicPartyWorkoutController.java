package com.team20.fitmate.domain.workout.controller;

import com.team20.fitmate.domain.participant.controller.ParticipantController;
import com.team20.fitmate.domain.workout.dto.PublicPartyFinishRequest;
import com.team20.fitmate.domain.workout.dto.WorkoutResponse;
import com.team20.fitmate.domain.workout.service.PublicPartyWorkoutService;
import com.team20.fitmate.global.rsdata.RsData;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 공개 파티 운동 기록 API. 루틴 없이 시작하고, 종료 때 본인 점핑잭 기록을 저장한다.
 * 결과는 운동 결과 조회 API({@code GET /api/workouts/{workoutId}/result})로 본다.
 */
@Tag(name = "공개 파티 운동 기록", description = "선셋 에어로빅 운동 시작·종료(본인 수행 기록 저장). 팀 경험치·2회차 추천 없음")
@RestController
@RequestMapping("/api/public-parties/workouts")
@RequiredArgsConstructor
public class PublicPartyWorkoutController {

    private final PublicPartyWorkoutService publicPartyWorkoutService;

    /**
     * 공개 파티 운동을 시작한다.
     */
    @Operation(summary = "공개 파티 운동 시작",
            description = "루틴 없이 운동 기록 식별값(workoutId)을 발급한다. 친구 파티 소속과 무관하게 참가자 토큰만 있으면 된다. "
                    + "진행 중인 공개 파티 기록이 있으면 새로 만들지 않고 그 기록을 돌려준다(재접속 시 이어 하기, 닫힌 뒤에도 가능). "
                    + "새 기록은 공개 파티가 열려 있을 때만 만든다(GET /api/public-parties/current의 status가 OPEN).")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "201-1 운동 기록 발급 (또는 진행 중인 기록 반환)"),
            @ApiResponse(responseCode = "400", description = "400-3 토큰 헤더 누락"),
            @ApiResponse(responseCode = "404", description = "404-2 참가자 없음"),
            @ApiResponse(responseCode = "409", description = "409-4 공개 파티가 열려 있지 않음(진행 중인 기록이 없을 때)")
    })
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public RsData<WorkoutResponse> start(
            @Parameter(description = "참가자 생성 때 받은 재방문 토큰", required = true)
            @RequestHeader(ParticipantController.TOKEN_HEADER) String clientToken) {
        return RsData.created("공개 파티 운동을 시작했습니다.", publicPartyWorkoutService.start(clientToken));
    }

    /**
     * 공개 파티 운동을 종료하고 본인 수행 기록을 저장한다.
     */
    @Operation(summary = "공개 파티 운동 종료",
            description = "본인 점핑잭 수행 기록을 확정 저장하고 종료(FINISHED)로 바꾼다. 팀 경험치는 쌓지 않는다. "
                    + "측정하지 못했으면 값을 null로 두고 NOT_MEASURED로 보낸다(0으로 보내지 않는다). 종료 후에는 다시 제출할 수 없다. "
                    + "파티가 닫힌 뒤에도 열려 있을 때 시작한 기록은 종료할 수 있다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "200-1 수행 기록 확정"),
            @ApiResponse(responseCode = "400", description = "400-1 필수 값 누락·음수, 400-2 본문 오류, 400-3 토큰 헤더 누락, "
                    + "400-4 측정 상태 규칙 위반"),
            @ApiResponse(responseCode = "404", description = "404-2 참가자 없음, "
                    + "404-5 공개 파티 운동 기록 없음(다른 참가자의 기록이나 루틴 운동 기록 포함)"),
            @ApiResponse(responseCode = "409", description = "409-1 이미 종료된 운동 기록")
    })
    @PostMapping("/{workoutId}/finish")
    public RsData<WorkoutResponse> finish(
            @Parameter(description = "참가자 생성 때 받은 재방문 토큰", required = true)
            @RequestHeader(ParticipantController.TOKEN_HEADER) String clientToken,
            @Parameter(description = "공개 파티 운동 시작 때 받은 운동 기록 식별값", required = true)
            @PathVariable("workoutId") Long workoutId,
            @Valid @RequestBody PublicPartyFinishRequest request) {
        return RsData.ok("공개 파티 운동 기록을 확정했습니다.",
                publicPartyWorkoutService.finish(clientToken, workoutId, request));
    }
}
