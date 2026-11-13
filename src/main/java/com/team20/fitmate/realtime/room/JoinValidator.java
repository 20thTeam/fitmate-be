package com.team20.fitmate.realtime.room;

import com.team20.fitmate.domain.contest.dto.ContestSeat;
import com.team20.fitmate.domain.contest.service.ContestService;
import com.team20.fitmate.domain.party.service.PublicPartyService;
import com.team20.fitmate.domain.participant.entity.Participant;
import com.team20.fitmate.domain.participant.service.ParticipantService;
import com.team20.fitmate.domain.room.entity.Room;
import com.team20.fitmate.domain.room.service.RoomService;
import com.team20.fitmate.global.exception.CustomException;
import com.team20.fitmate.global.exception.ErrorCode;
import com.team20.fitmate.realtime.message.RealtimeErrorCode;
import com.team20.fitmate.realtime.message.RealtimeException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * JOIN 요청을 DB 기준으로 확인한다. 참가자가 있는지, 운동방이 있는지, REST 입장을 마친 방인지 본다.
 * <p>
 * 공개 파티(이슈 #11) 입장은 REST 입장 없이 참가자와 오픈 상태만 본다.
 * 챌린지 판(이슈 #34) 입장은 참가자 토큰 없이 playerId가 그 판의 참가자인지와 판이 끝나지 않았는지만 본다.
 * <p>
 * 실시간 계층에서 DB를 읽는 곳은 여기 하나다. 필요한 값은 트랜잭션 안에서 {@link JoinTicket}으로 옮겨 담는다.
 * 정원 판단은 DB가 아니라 {@link RoomSessionRegistry}에서 현재 연결 수로 한다(이슈 #2 B와 합의).
 */
@Service
@RequiredArgsConstructor
public class JoinValidator {

    private final ParticipantService participantService;
    private final RoomService roomService;
    private final PublicPartyService publicPartyService;
    private final ContestService contestService;

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

    /**
     * 공개 파티 입장을 확인한다. 친구 파티 소속(REST 입장)과 관계없이 임시 프로필(참가자)만 있으면 된다.
     * <p>
     * 이미 들어와 있던 참가자의 재접속도 닫힌 뒤에는 거부한다(닫힌 파티에 새로 붙는 연결을 만들지 않는다).
     *
     * @throws RealtimeException 참가자가 없으면 {@link RealtimeErrorCode#PARTICIPANT_NOT_FOUND},
     *                           파티가 없으면 {@link RealtimeErrorCode#PUBLIC_PARTY_NOT_FOUND},
     *                           닫혀 있으면 {@link RealtimeErrorCode#PUBLIC_PARTY_CLOSED}
     */
    @Transactional(readOnly = true)
    public JoinTicket validatePublicParty(String token, Long partyId) {
        Participant participant = findParticipant(token);
        try {
            publicPartyService.validateJoinable(partyId);
        } catch (CustomException e) {
            throw new RealtimeException(e.getErrorCode() == ErrorCode.PUBLIC_PARTY_CLOSED
                    ? RealtimeErrorCode.PUBLIC_PARTY_CLOSED
                    : RealtimeErrorCode.PUBLIC_PARTY_NOT_FOUND);
        }
        return JoinTicket.publicParty(partyId,
                participant.getId(), participant.getNickname(), participant.getCharacterCode());
    }

    /**
     * 챌린지 판 입장을 확인한다(이슈 #34). 판 참가(REST join)가 준 playerId가 그 판의 참가자인지만 본다.
     * <p>
     * 참가자 토큰이 없어서, 남의 playerId를 알면 대신 들어올 수 있다. 시연장 노트북 4대 전용이라 받아들인 위험이다.
     * 끝난 판에는 재접속도 받지 않는다.
     *
     * @throws RealtimeException 판이 없으면 {@link RealtimeErrorCode#CONTEST_GAME_NOT_FOUND},
     *                           판의 참가자가 아니면 {@link RealtimeErrorCode#CONTEST_PLAYER_NOT_FOUND},
     *                           판이 끝났으면 {@link RealtimeErrorCode#CONTEST_GAME_FINISHED}
     */
    public JoinTicket validateContestGame(Long gameId, Long playerId) {
        ContestSeat seat;
        try {
            seat = contestService.findSeat(gameId, playerId);
        } catch (CustomException e) {
            throw new RealtimeException(e.getErrorCode() == ErrorCode.CONTEST_GAME_NOT_FOUND
                    ? RealtimeErrorCode.CONTEST_GAME_NOT_FOUND
                    : RealtimeErrorCode.CONTEST_PLAYER_NOT_FOUND);
        }
        if (seat.finished()) {
            throw new RealtimeException(RealtimeErrorCode.CONTEST_GAME_FINISHED);
        }
        return JoinTicket.contestGame(seat.gameId(), seat.maxPlayers(), seat.playerId(), seat.nickname());
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
