package com.team20.fitmate.domain.room.controller;

import com.team20.fitmate.domain.participant.controller.ParticipantController;
import com.team20.fitmate.domain.room.dto.RoomCreateRequest;
import com.team20.fitmate.domain.room.dto.RoomResponse;
import com.team20.fitmate.domain.room.service.RoomService;
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
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 운동방 API. 운동방을 조회·만들고 참가자를 운동방에 연결하거나 내보낸다.
 */
@Tag(name = "운동방", description = "운동방 조회·만들기와 입장·나가기")
@RestController
@RequestMapping("/api/rooms")
@RequiredArgsConstructor
public class RoomController {

    private final RoomService roomService;

    /**
     * 운동방 목록을 조회한다.
     */
    @Operation(summary = "운동방 목록 조회", description = "만든 순서대로 돌려준다. 첫 번째 방이 지정 운동방이고, "
            + "시연용 운동방(`운동방 1` …)과 참가자가 만든 방이 뒤에 온다. 방마다 팀이 다르다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "200-1 조회 성공")
    })
    @GetMapping
    public RsData<List<RoomResponse>> getAll() {
        return RsData.ok("운동방 목록을 조회했습니다.", roomService.getAll());
    }

    /**
     * 운동방 하나를 조회한다.
     */
    @Operation(summary = "운동방 조회", description = "운동방 정보와 정원을 조회한다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "200-1 조회 성공"),
            @ApiResponse(responseCode = "400", description = "400-3 roomId 형식 오류"),
            @ApiResponse(responseCode = "404", description = "404-4 운동방 없음")
    })
    @GetMapping("/{roomId}")
    public RsData<RoomResponse> get(
            @Parameter(description = "운동방 식별값", required = true)
            @PathVariable("roomId") Long roomId) {
        return RsData.ok("운동방을 조회했습니다.", roomService.get(roomId));
    }

    /**
     * 참가자를 운동방에 연결한다. 실시간 연결(WebSocket)은 이 요청 뒤에 따로 맺는다.
     */
    @Operation(summary = "운동방 입장",
            description = "로그인·초대 없이 참가자를 운동방에 연결한다. 같은 방에 다시 입장해도 성공한다. "
                    + "정원 초과는 실시간 연결 단계에서 판단한다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "200-1 입장 성공"),
            @ApiResponse(responseCode = "400", description = "400-3 토큰 헤더 누락 또는 roomId 형식 오류"),
            @ApiResponse(responseCode = "404", description = "404-2 참가자 없음, 404-4 운동방 없음")
    })
    @PostMapping("/{roomId}/enter")
    public RsData<RoomResponse> enter(
            @Parameter(description = "참가자 생성 때 받은 재방문 토큰", required = true)
            @RequestHeader(ParticipantController.TOKEN_HEADER) String clientToken,
            @Parameter(description = "운동방 식별값", required = true)
            @PathVariable("roomId") Long roomId) {
        return RsData.ok("운동방에 입장했습니다.", roomService.enter(clientToken, roomId));
    }

    /**
     * 운동방을 만든다. 팀도 같이 만든다(방 = 팀).
     */
    @Operation(summary = "운동방 만들기",
            description = "이름(1~20자, 중복 허용)으로 정원 5명인 운동방과 그 팀을 만든다. "
                    + "만든 사람을 입장시키지는 않으므로 이어서 입장 API를 부른다. "
                    + "서버 전체 운동방 수가 상한(임시 20개)이면 409-5.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "200-1 만들기 성공"),
            @ApiResponse(responseCode = "400", description = "400-1 이름 누락·20자 초과, 400-2 본문 오류, 400-3 토큰 헤더 누락"),
            @ApiResponse(responseCode = "404", description = "404-2 참가자 없음"),
            @ApiResponse(responseCode = "409", description = "409-5 운동방 수 상한 초과")
    })
    @PostMapping
    public RsData<RoomResponse> create(
            @Parameter(description = "참가자 생성 때 받은 재방문 토큰", required = true)
            @RequestHeader(ParticipantController.TOKEN_HEADER) String clientToken,
            @Valid @RequestBody RoomCreateRequest request) {
        return RsData.ok("운동방을 만들었습니다.", roomService.create(clientToken, request));
    }

    /**
     * 참가자를 운동방에서 내보낸다.
     */
    @Operation(summary = "운동방 나가기",
            description = "참가자의 마지막 입장 방을 비운다. 이후 GET /api/participants/me의 roomId는 null이다. "
                    + "이미 나갔거나 다른 방에 있으면 바꾸지 않고 성공한다. "
                    + "실시간 연결은 닫지 않으므로, 연결 중이면 프론트가 LEAVE를 보내고 연결을 닫는다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "200-1 나가기 성공"),
            @ApiResponse(responseCode = "400", description = "400-3 토큰 헤더 누락 또는 roomId 형식 오류"),
            @ApiResponse(responseCode = "404", description = "404-2 참가자 없음, 404-4 운동방 없음")
    })
    @PostMapping("/{roomId}/leave")
    public RsData<Void> leave(
            @Parameter(description = "참가자 생성 때 받은 재방문 토큰", required = true)
            @RequestHeader(ParticipantController.TOKEN_HEADER) String clientToken,
            @Parameter(description = "운동방 식별값", required = true)
            @PathVariable("roomId") Long roomId) {
        roomService.leave(clientToken, roomId);
        return RsData.ok("운동방에서 나갔습니다.", null);
    }
}
