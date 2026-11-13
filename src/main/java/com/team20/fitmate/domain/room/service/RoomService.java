package com.team20.fitmate.domain.room.service;

import com.team20.fitmate.domain.participant.entity.Participant;
import com.team20.fitmate.domain.participant.service.ParticipantService;
import com.team20.fitmate.domain.room.config.RoomProperties;
import com.team20.fitmate.domain.room.dto.RoomCreateRequest;
import com.team20.fitmate.domain.room.dto.RoomResponse;
import com.team20.fitmate.domain.room.entity.Room;
import com.team20.fitmate.domain.room.entity.Team;
import com.team20.fitmate.domain.room.init.RoomDataInitializer;
import com.team20.fitmate.domain.room.repository.RoomRepository;
import com.team20.fitmate.domain.room.repository.TeamRepository;
import com.team20.fitmate.global.exception.CustomException;
import com.team20.fitmate.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 운동방 조회·만들기와 입장·나가기(참가자 연결)를 맡는 서비스.
 */
@Service
@RequiredArgsConstructor
public class RoomService {

    private final RoomRepository roomRepository;
    private final TeamRepository teamRepository;
    private final ParticipantService participantService;
    private final RoomProperties roomProperties;

    /**
     * 모든 운동방을 만든 순서대로 조회한다. 첫 번째가 지정 운동방이다.
     */
    @Transactional(readOnly = true)
    public List<RoomResponse> getAll() {
        return roomRepository.findAllByOrderByIdAsc().stream()
                .map(RoomResponse::from)
                .toList();
    }

    /**
     * 운동방 하나를 조회한다.
     */
    @Transactional(readOnly = true)
    public RoomResponse get(Long roomId) {
        return RoomResponse.from(findById(roomId));
    }

    /**
     * 참가자를 운동방에 연결한다. 이미 같은 방이면 그대로 둔다.
     * <p>
     * 정원 초과 판단은 하지 않는다. 동시 접속 인원은 실시간 계층에서 연결 수로 판단한다.
     */
    @Transactional
    public RoomResponse enter(String clientToken, Long roomId) {
        Participant participant = participantService.findByClientToken(clientToken);
        Room room = findById(roomId);
        participant.enterRoom(room);
        return RoomResponse.from(room);
    }

    /**
     * 운동방을 만든다. 방마다 팀을 하나 같이 만들어 팀 경험치가 다른 방과 섞이지 않게 한다(방 = 팀).
     * <p>
     * 만든 사람을 방에 입장시키지는 않는다. 프론트가 이어서 입장 API를 부른다.
     * 동시에 여러 명이 만들면 상한을 조금 넘을 수 있다(시연 규모라 잠금은 두지 않는다).
     *
     * @throws CustomException 참가자가 없으면 {@link ErrorCode#PARTICIPANT_NOT_FOUND},
     *                         운동방 수가 상한이면 {@link ErrorCode#ROOM_LIMIT_EXCEEDED}
     */
    @Transactional
    public RoomResponse create(String clientToken, RoomCreateRequest request) {
        participantService.findByClientToken(clientToken);
        if (roomRepository.count() >= roomProperties.maxCount()) {
            throw new CustomException(ErrorCode.ROOM_LIMIT_EXCEEDED);
        }
        String name = request.name().strip();
        Team team = teamRepository.save(Team.create(RoomDataInitializer.teamNameOf(name)));
        Room room = roomRepository.save(Room.create(team, name, roomProperties.capacity()));
        return RoomResponse.from(room);
    }

    /**
     * 참가자를 운동방에서 내보낸다. 이후 참가자의 마지막 입장 방(roomId)은 null이 된다.
     * <p>
     * 이미 나갔거나 다른 방에 들어가 있으면 아무것도 바꾸지 않고 성공한다(다른 방 입장 기록을 지우지 않는다).
     * 실시간 연결(/ws)은 건드리지 않는다. 연결 중이면 프론트가 LEAVE를 보내고 연결을 닫는다.
     *
     * @throws CustomException 참가자가 없으면 {@link ErrorCode#PARTICIPANT_NOT_FOUND},
     *                         운동방이 없으면 {@link ErrorCode#ROOM_NOT_FOUND}
     */
    @Transactional
    public void leave(String clientToken, Long roomId) {
        Participant participant = participantService.findByClientToken(clientToken);
        Room room = findById(roomId);
        if (participant.getRoom() != null && participant.getRoom().getId().equals(room.getId())) {
            participant.leaveRoom();
        }
    }

    /**
     * 운동방 엔티티를 찾는다. 실시간 계층 등 다른 곳에서 방 존재 확인에 쓴다.
     *
     * @throws CustomException 운동방이 없으면 {@link ErrorCode#ROOM_NOT_FOUND}
     */
    @Transactional(readOnly = true)
    public Room findById(Long roomId) {
        return roomRepository.findById(roomId)
                .orElseThrow(() -> new CustomException(ErrorCode.ROOM_NOT_FOUND));
    }
}
