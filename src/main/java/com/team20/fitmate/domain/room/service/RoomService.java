package com.team20.fitmate.domain.room.service;

import com.team20.fitmate.domain.participant.entity.Participant;
import com.team20.fitmate.domain.participant.service.ParticipantService;
import com.team20.fitmate.domain.room.dto.RoomResponse;
import com.team20.fitmate.domain.room.entity.Room;
import com.team20.fitmate.domain.room.repository.RoomRepository;
import com.team20.fitmate.global.exception.CustomException;
import com.team20.fitmate.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 운동방 조회와 입장(참가자 연결)을 맡는 서비스.
 */
@Service
@RequiredArgsConstructor
public class RoomService {

    private final RoomRepository roomRepository;
    private final ParticipantService participantService;

    /**
     * 모든 운동방을 조회한다. 시연에서는 지정 운동방 하나만 있다.
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
