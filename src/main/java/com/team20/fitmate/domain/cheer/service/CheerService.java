package com.team20.fitmate.domain.cheer.service;

import com.team20.fitmate.domain.cheer.dto.CheerRecordCommand;
import com.team20.fitmate.domain.cheer.entity.Cheer;
import com.team20.fitmate.domain.cheer.repository.CheerRepository;
import com.team20.fitmate.domain.participant.entity.Participant;
import com.team20.fitmate.domain.participant.repository.ParticipantRepository;
import com.team20.fitmate.domain.room.entity.Room;
import com.team20.fitmate.domain.room.repository.RoomRepository;
import com.team20.fitmate.global.exception.CustomException;
import com.team20.fitmate.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 실시간 계층이 전달한 응원을 저장하는 {@link CheerRecorder} 구현체(이슈 #6).
 * <p>
 * 실시간 계층이 같은 방 참가자에게만 전달하지만, 여기서도 보낸 사람과 받은 사람이 모두 그 운동방에
 * 입장해 있는지 다시 확인한다. 메시지 처리 스레드에서 불리므로 조회 3번과 저장 1번만 한다.
 */
@Service
@RequiredArgsConstructor
public class CheerService implements CheerRecorder {

    private final CheerRepository cheerRepository;
    private final RoomRepository roomRepository;
    private final ParticipantRepository participantRepository;

    /**
     * 전달된 응원 하나를 저장한다.
     *
     * @throws CustomException 운동방이 없으면 {@link ErrorCode#ROOM_NOT_FOUND},
     *                         참가자가 없으면 {@link ErrorCode#PARTICIPANT_NOT_FOUND},
     *                         다른 방 참가자이거나 자기 자신이면 {@link ErrorCode#INVALID_CHEER}
     */
    @Override
    @Transactional
    public void record(CheerRecordCommand command) {
        Room room = roomRepository.findById(command.roomId())
                .orElseThrow(() -> new CustomException(ErrorCode.ROOM_NOT_FOUND));
        Participant sender = findParticipant(command.senderId());
        Participant receiver = findParticipant(command.receiverId());
        cheerRepository.save(Cheer.of(room, sender, receiver, command.reaction(), command.occurredAt()));
    }

    /** 참가자를 식별값으로 찾는다 */
    private Participant findParticipant(Long participantId) {
        return participantRepository.findById(participantId)
                .orElseThrow(() -> new CustomException(ErrorCode.PARTICIPANT_NOT_FOUND));
    }
}
