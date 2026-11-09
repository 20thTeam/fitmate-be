package com.team20.fitmate.realtime.room;

import com.team20.fitmate.domain.participant.entity.Participant;
import com.team20.fitmate.domain.participant.service.ParticipantService;
import com.team20.fitmate.domain.room.entity.Room;
import com.team20.fitmate.domain.room.service.RoomService;
import com.team20.fitmate.global.exception.CustomException;
import com.team20.fitmate.realtime.message.RealtimeErrorCode;
import com.team20.fitmate.realtime.message.RealtimeException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * JOIN 요청을 DB 기준으로 확인한다. 참가자가 있는지, 운동방이 있는지, REST 입장을 마친 방인지 본다.
 * <p>
 * 실시간 계층에서 DB를 읽는 곳은 여기 하나다. 필요한 값은 트랜잭션 안에서 {@link JoinTicket}으로 옮겨 담는다.
 * 정원 판단은 DB가 아니라 {@link RoomSessionRegistry}에서 현재 연결 수로 한다(이슈 #2 B와 합의).
 */
@Service
@RequiredArgsConstructor
public class JoinValidator {

    private final ParticipantService participantService;
    private final RoomService roomService;

    /**
     * 토큰과 운동방 식별값을 확인하고 입장 정보를 돌려준다.
     *
     * @throws RealtimeException 참가자가 없으면 {@link RealtimeErrorCode#PARTICIPANT_NOT_FOUND},
     *                           운동방이 없으면 {@link RealtimeErrorCode#ROOM_NOT_FOUND},
     *                           REST 입장을 하지 않은 방이면 {@link RealtimeErrorCode#NOT_ENTERED_ROOM}
     */
    @Transactional(readOnly = true)
    public JoinTicket validate(String token, Long roomId) {
        Participant participant = findParticipant(token);
        Room room = findRoom(roomId);

        Room enteredRoom = participant.getRoom();
        if (enteredRoom == null || !enteredRoom.getId().equals(room.getId())) {
            throw new RealtimeException(RealtimeErrorCode.NOT_ENTERED_ROOM);
        }
        return new JoinTicket(room.getId(), room.getCapacity(),
                participant.getId(), participant.getNickname(), participant.getCharacterCode());
    }

    /** 토큰으로 참가자를 찾는다. REST 오류를 실시간 오류로 바꾼다 */
    private Participant findParticipant(String token) {
        try {
            return participantService.findByClientToken(token);
        } catch (CustomException e) {
            throw new RealtimeException(RealtimeErrorCode.PARTICIPANT_NOT_FOUND);
        }
    }

    /** 운동방을 찾는다. REST 오류를 실시간 오류로 바꾼다 */
    private Room findRoom(Long roomId) {
        try {
            return roomService.findById(roomId);
        } catch (CustomException e) {
            throw new RealtimeException(RealtimeErrorCode.ROOM_NOT_FOUND);
        }
    }
}
