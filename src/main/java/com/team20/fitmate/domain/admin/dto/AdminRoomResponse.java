package com.team20.fitmate.domain.admin.dto;

import com.team20.fitmate.domain.room.entity.Room;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * 운영 화면의 운동방 접속자 현황. {@code GET /api/rooms} 응답에 입장 참가자 목록을 더한 형태다.
 *
 * @param roomId       운동방 식별값
 * @param name         운동방 이름
 * @param capacity     정원 (동시 접속 인원)
 * @param teamId       팀 식별값
 * @param teamName     팀 이름
 * @param participants REST 입장을 한 참가자 (입장 순이 아니라 참가자 식별값 순)
 */
public record AdminRoomResponse(

        @Schema(description = "운동방 식별값", example = "1")
        Long roomId,

        @Schema(description = "운동방 이름", example = "FITMATE 운동방")
        String name,

        @Schema(description = "정원 (동시 접속 인원)", example = "5")
        int capacity,

        @Schema(description = "팀 식별값", example = "1")
        Long teamId,

        @Schema(description = "팀 이름", example = "FITMATE 팀")
        String teamName,

        @Schema(description = "REST 입장(POST /api/rooms/{roomId}/enter)을 한 참가자")
        List<AdminRoomParticipantResponse> participants
) {

    /**
     * 운동방과 참가자 목록으로 응답을 만든다. 팀(LAZY)을 읽으므로 트랜잭션 안에서 부른다.
     */
    public static AdminRoomResponse of(Room room, List<AdminRoomParticipantResponse> participants) {
        return new AdminRoomResponse(
                room.getId(),
                room.getName(),
                room.getCapacity(),
                room.getTeam().getId(),
                room.getTeam().getName(),
                participants);
    }
}
