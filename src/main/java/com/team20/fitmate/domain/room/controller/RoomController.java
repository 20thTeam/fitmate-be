package com.team20.fitmate.domain.room.controller;

import com.team20.fitmate.domain.participant.controller.ParticipantController;
import com.team20.fitmate.domain.room.dto.RoomResponse;
import com.team20.fitmate.domain.room.service.RoomService;
import com.team20.fitmate.global.rsdata.RsData;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 운동방 API. 지정 운동방을 조회하고 참가자를 운동방에 연결한다.
 */
@Tag(name = "운동방", description = "지정 운동방 조회와 입장")
@RestController
@RequestMapping("/api/rooms")
@RequiredArgsConstructor
public class RoomController {

    private final RoomService roomService;

    /**
     * 운동방 목록을 조회한다.
     */
    @Operation(summary = "운동방 목록 조회", description = "시연에서는 지정 운동방 하나만 있다. 첫 번째 방이 지정 운동방이다.")
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
}
