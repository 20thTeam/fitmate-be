package com.team20.fitmate.domain.room.dto;

import com.team20.fitmate.domain.room.entity.Room;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 운동방 응답.
 *
 * @param roomId   운동방 식별값
 * @param name     운동방 이름
 * @param capacity 정원 (동시 접속 인원)
 * @param teamId   팀 식별값
 * @param teamName 팀 이름
 * @param participantCount 지금 실시간(/ws)으로 들어와 있는 인원(재접속 대기 포함, 정원 판단과 같은 기준)
 */
public record RoomResponse(

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

        @Schema(description = "지금 실시간(/ws)으로 들어와 있는 인원. 재접속을 기다리는 참가자도 센다(정원 판단과 같은 기준)",
                example = "3")
        int participantCount
) {

    /**
     * 운동방 엔티티와 현재 인원을 응답으로 바꾼다. 팀(LAZY)을 읽으므로 트랜잭션 안에서 부른다.
     */
    public static RoomResponse of(Room room, int participantCount) {
        return new RoomResponse(
                room.getId(),
                room.getName(),
                room.getCapacity(),
                room.getTeam().getId(),
                room.getTeam().getName(),
                participantCount
        );
    }
}
