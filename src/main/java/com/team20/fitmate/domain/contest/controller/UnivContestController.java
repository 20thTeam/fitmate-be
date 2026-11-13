package com.team20.fitmate.domain.contest.controller;

import com.team20.fitmate.domain.contest.dto.ContestGameResponse;
import com.team20.fitmate.domain.contest.dto.ContestJoinRequest;
import com.team20.fitmate.domain.contest.dto.ContestJoinResponse;
import com.team20.fitmate.domain.contest.dto.ContestPlayerRequest;
import com.team20.fitmate.domain.contest.dto.ContestResultRequest;
import com.team20.fitmate.domain.contest.dto.ContestStandingsResponse;
import com.team20.fitmate.domain.contest.service.ContestService;
import com.team20.fitmate.global.rsdata.RsData;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 학교 대항 스쿼트 20초 챌린지 API (이슈 #34, 시연장 이벤트 화면 {@code /univ-contest}).
 * <p>
 * 로그인·참가자 토큰 없이 쓴다. 실시간 연결 없이 프론트가 대기·운동 중 1초마다 판 상태를, 5초마다 현황을 조회한다.
 * 모든 판 응답에 서버 시각({@code serverNow})을 넣어 노트북마다 다른 시계를 맞추게 한다.
 */
@Tag(name = "학교 대항 스쿼트 챌린지", description = "시연장 이벤트 /univ-contest. 판 참가·시작·결과·나가기와 학교별 현황. 로그인 없음")
@RestController
@RequestMapping("/api/univ-contest")
@RequiredArgsConstructor
public class UnivContestController {

    private final ContestService contestService;

    /**
     * 학교별 누적·상위 기록 현황을 조회한다.
     */
    @Operation(summary = "현황 조회",
            description = "끝난(FINISHED) 판에서 측정된(reps가 null이 아닌) 기록만 집계한다. "
                    + "schools는 학교별 합계(totalReps 큰 순), topPlayers는 한 판 기록 상위 5개(reps 큰 순), "
                    + "gameCount는 측정된 기록이 하나라도 있는 끝난 판 수다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "200-1 현황 조회")
    })
    @GetMapping("/standings")
    public RsData<ContestStandingsResponse> getStandings() {
        return RsData.ok("챌린지 현황을 조회했습니다.", contestService.getStandings());
    }

    /**
     * 판에 들어간다.
     */
    @Operation(summary = "판 참가",
            description = "모집 중(WAITING)이고 4명 미만인 판이 있으면 가장 오래된 판에 넣고, 없으면 새 판을 만든다. "
                    + "판이 가득 차도 오류가 아니라 새 판에 들어간다. 받은 playerId로 시작·결과·나가기를 요청한다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "200-1 참가 (playerId와 판 상태)"),
            @ApiResponse(responseCode = "400", description = "400-1 닉네임·학교 누락 또는 길이 초과, 400-2 본문 오류")
    })
    @PostMapping("/games/join")
    public RsData<ContestJoinResponse> join(@Valid @RequestBody ContestJoinRequest request) {
        return RsData.ok("챌린지 판에 참가했습니다.", contestService.join(request));
    }

    /**
     * 판 상태를 조회한다.
     */
    @Operation(summary = "판 상태 조회",
            description = "status는 서버 시각 기준으로 계산한다. WAITING → COUNTDOWN(startAt 전) → RUNNING(startAt~+20초, 이후 제출 대기) "
                    + "→ FINISHED(모두 제출 또는 startAt+30초 경과). serverNow로 시계를 맞춘다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "200-1 판 상태"),
            @ApiResponse(responseCode = "400", description = "400-3 gameId 형식 오류"),
            @ApiResponse(responseCode = "404", description = "404-9 판 없음")
    })
    @GetMapping("/games/{gameId}")
    public RsData<ContestGameResponse> getGame(
            @Parameter(description = "판 식별값", required = true, example = "17")
            @PathVariable("gameId") Long gameId) {
        return RsData.ok("챌린지 판을 조회했습니다.", contestService.getGame(gameId));
    }

    /**
     * 판을 시작한다.
     */
    @Operation(summary = "판 시작",
            description = "startAt = 지금 + 5초로 정한다. 이미 시작된 판이면 startAt을 바꾸지 않고 그대로 돌려준다"
                    + "(여러 명이 동시에 눌러도 startAt은 하나).")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "200-1 시작 (또는 이미 시작된 판 반환)"),
            @ApiResponse(responseCode = "400", description = "400-1 playerId 누락, 400-2 본문 오류, 400-3 gameId 형식 오류"),
            @ApiResponse(responseCode = "404", description = "404-9 판 없음, 404-10 이 판에 참가하지 않은 playerId")
    })
    @PostMapping("/games/{gameId}/start")
    public RsData<ContestGameResponse> start(
            @Parameter(description = "판 식별값", required = true, example = "17")
            @PathVariable("gameId") Long gameId,
            @Valid @RequestBody ContestPlayerRequest request) {
        return RsData.ok("챌린지 판을 시작했습니다.", contestService.start(gameId, request.playerId()));
    }

    /**
     * 한 판 결과를 낸다.
     */
    @Operation(summary = "결과 제출",
            description = "reps는 0 이상 정수, 측정 안 됨(자세가 한 번도 인식되지 않음)이면 null(0으로 채우지 않는다). "
                    + "같은 사람이 두 번 내면 처음 값을 유지하고 성공으로 돌려준다. "
                    + "판이 이미 끝났으면(startAt+30초 경과) 값을 받지 않고 지금 상태를 돌려준다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "200-1 제출 (또는 이미 낸 값·끝난 판 그대로 반환)"),
            @ApiResponse(responseCode = "400", description = "400-1 playerId 누락·reps 음수, 400-2 본문 오류, 400-3 gameId 형식 오류"),
            @ApiResponse(responseCode = "404", description = "404-9 판 없음, 404-10 이 판에 참가하지 않은 playerId"),
            @ApiResponse(responseCode = "409", description = "409-5 아직 시작하지 않은 판")
    })
    @PostMapping("/games/{gameId}/result")
    public RsData<ContestGameResponse> submitResult(
            @Parameter(description = "판 식별값", required = true, example = "17")
            @PathVariable("gameId") Long gameId,
            @Valid @RequestBody ContestResultRequest request) {
        return RsData.ok("챌린지 결과를 받았습니다.",
                contestService.submitResult(gameId, request.playerId(), request.reps()));
    }

    /**
     * 시작 전에 판에서 나간다.
     */
    @Operation(summary = "판 나가기",
            description = "시작 전(WAITING)에만 판에서 뺀다. 시작 후거나 이미 나간 playerId면 아무것도 하지 않고 판 상태를 돌려준다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "200-1 나가기 (또는 무시하고 판 상태 반환)"),
            @ApiResponse(responseCode = "400", description = "400-1 playerId 누락, 400-2 본문 오류, 400-3 gameId 형식 오류"),
            @ApiResponse(responseCode = "404", description = "404-9 판 없음")
    })
    @PostMapping("/games/{gameId}/leave")
    public RsData<ContestGameResponse> leave(
            @Parameter(description = "판 식별값", required = true, example = "17")
            @PathVariable("gameId") Long gameId,
            @Valid @RequestBody ContestPlayerRequest request) {
        return RsData.ok("챌린지 판에서 나갔습니다.", contestService.leave(gameId, request.playerId()));
    }
}
