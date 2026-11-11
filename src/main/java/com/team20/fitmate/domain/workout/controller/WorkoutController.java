package com.team20.fitmate.domain.workout.controller;

import com.team20.fitmate.domain.participant.controller.ParticipantController;
import com.team20.fitmate.domain.workout.dto.WorkoutFinishRequest;
import com.team20.fitmate.domain.workout.dto.WorkoutResponse;
import com.team20.fitmate.domain.workout.dto.WorkoutResultResponse;
import com.team20.fitmate.domain.workout.dto.WorkoutStartRequest;
import com.team20.fitmate.domain.workout.service.WorkoutService;
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
 * 운동 기록 API. 운동 시작 시 기록 식별값을 발급하고, 종료 시 운동별 수행 기록을 확정하며, 결과를 조회한다.
 */
@Tag(name = "운동 기록", description = "운동 시작(기록 식별값 발급), 종료(수행 기록 확정·팀 경험치 적립), 결과 조회")
@RestController
@RequestMapping("/api/workouts")
@RequiredArgsConstructor
public class WorkoutController {

    private final WorkoutService workoutService;

    /**
     * 루틴으로 운동을 시작한다.
     */
    @Operation(summary = "운동 시작",
            description = "운동 기록 식별값(workoutId)을 발급하고 진행 중(IN_PROGRESS)으로 둔다. "
                    + "같은 루틴으로 진행 중인 기록이 있으면 새로 만들지 않고 그 기록을 돌려준다(재접속 시 이어 하기). "
                    + "운동방은 참가자가 지금 입장한 방으로 기록한다.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "201-1 운동 기록 발급 (또는 진행 중인 기록 반환)"),
            @ApiResponse(responseCode = "400", description = "400-1 루틴 식별값 누락, 400-2 본문 오류, 400-3 토큰 헤더 누락"),
            @ApiResponse(responseCode = "404", description = "404-2 참가자 없음, 404-3 루틴 없음(다른 참가자의 루틴 포함)"),
            @ApiResponse(responseCode = "409", description = "409-2 추천 불가 등 시작할 수 없는 루틴")
    })
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public RsData<WorkoutResponse> start(
            @Parameter(description = "참가자 생성 때 받은 재방문 토큰", required = true)
            @RequestHeader(ParticipantController.TOKEN_HEADER) String clientToken,
            @Valid @RequestBody WorkoutStartRequest request) {
        return RsData.created("운동을 시작했습니다.", workoutService.start(clientToken, request.routineId()));
    }

    /**
     * 운동을 종료하고 운동별 수행 기록을 확정한다.
     */
    @Operation(summary = "운동 종료",
            description = "운동별 수행 기록을 확정 저장하고 종료(FINISHED)로 바꾼 뒤 팀 경험치를 적립한다. "
                    + "형식은 docs/수행기록_형식.md. 보내지 않은 운동은 측정 못 함(NOT_MEASURED, 값 null)으로 저장한다. "
                    + "종료 후에는 다시 제출할 수 없다. 결과 화면은 결과 조회 API로 그린다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "200-1 수행 기록 확정"),
            @ApiResponse(responseCode = "400", description = "400-1 필수 값 누락·음수, 400-2 본문 오류, 400-3 토큰 헤더 누락, "
                    + "400-4 루틴에 없는 운동·같은 운동 중복·측정 상태 규칙 위반·공개 파티 기록(공개 파티 종료 API 사용)"),
            @ApiResponse(responseCode = "404", description = "404-2 참가자 없음, 404-5 운동 기록 없음(다른 참가자의 기록 포함)"),
            @ApiResponse(responseCode = "409", description = "409-1 이미 종료된 운동 기록")
    })
    @PostMapping("/{workoutId}/finish")
    public RsData<WorkoutResponse> finish(
            @Parameter(description = "참가자 생성 때 받은 재방문 토큰", required = true)
            @RequestHeader(ParticipantController.TOKEN_HEADER) String clientToken,
            @Parameter(description = "운동 시작 때 받은 운동 기록 식별값", required = true)
            @PathVariable("workoutId") Long workoutId,
            @Valid @RequestBody WorkoutFinishRequest request) {
        return RsData.ok("운동 기록을 확정했습니다.", workoutService.finish(clientToken, workoutId, request));
    }

    /**
     * 본인 운동 기록의 결과를 조회한다.
     */
    @Operation(summary = "운동 결과 조회",
            description = "결과 화면용. 확정된 운동별 수행 기록과 이번 운동으로 달라진 팀 경험치를 돌려준다. "
                    + "본인 기록만 조회할 수 있다. 측정 못 한 값은 null이다. "
                    + "팀 경험치는 teamXp.status가 ACCRUED일 때만 수치가 있다(산식 미정이면 FORMULA_UNDECIDED).")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "200-1 조회 성공"),
            @ApiResponse(responseCode = "400", description = "400-3 토큰 헤더 누락 또는 workoutId 형식 오류"),
            @ApiResponse(responseCode = "404", description = "404-2 참가자 없음, 404-5 운동 기록 없음(다른 참가자의 기록 포함)")
    })
    @GetMapping("/{workoutId}/result")
    public RsData<WorkoutResultResponse> getResult(
            @Parameter(description = "참가자 생성 때 받은 재방문 토큰", required = true)
            @RequestHeader(ParticipantController.TOKEN_HEADER) String clientToken,
            @Parameter(description = "운동 기록 식별값", required = true)
            @PathVariable("workoutId") Long workoutId) {
        return RsData.ok("운동 결과를 조회했습니다.", workoutService.getResult(clientToken, workoutId));
    }
}
