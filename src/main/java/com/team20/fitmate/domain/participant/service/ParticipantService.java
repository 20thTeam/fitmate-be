package com.team20.fitmate.domain.participant.service;

import com.team20.fitmate.domain.participant.dto.ParticipantCreateRequest;
import com.team20.fitmate.domain.participant.dto.ParticipantCreateResponse;
import com.team20.fitmate.domain.participant.dto.ParticipantResponse;
import com.team20.fitmate.domain.participant.entity.Participant;
import com.team20.fitmate.domain.participant.repository.ParticipantRepository;
import com.team20.fitmate.global.exception.CustomException;
import com.team20.fitmate.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 참가자 발급과 조회를 맡는 서비스.
 */
@Service
@RequiredArgsConstructor
public class ParticipantService {

    private final ParticipantRepository participantRepository;

    /**
     * 닉네임과 캐릭터로 새 참가자를 발급한다.
     * 같은 닉네임이 있어도 항상 새 참가자와 새 재방문 토큰을 만든다.
     */
    @Transactional
    public ParticipantCreateResponse create(ParticipantCreateRequest request) {
        Participant participant = participantRepository.save(request.toEntity());
        return ParticipantCreateResponse.from(participant);
    }

    /**
     * 재방문 토큰으로 본인 참가자 정보를 조회한다.
     */
    @Transactional(readOnly = true)
    public ParticipantResponse getByClientToken(String clientToken) {
        return ParticipantResponse.from(findByClientToken(clientToken));
    }

    /**
     * 재방문 토큰으로 참가자 엔티티를 찾는다. 다른 도메인(루틴 등)에서 본인 확인에 쓴다.
     *
     * @throws CustomException 토큰에 해당하는 참가자가 없으면 {@link ErrorCode#PARTICIPANT_NOT_FOUND}
     */
    @Transactional(readOnly = true)
    public Participant findByClientToken(String clientToken) {
        return participantRepository.findByClientToken(clientToken)
                .orElseThrow(() -> new CustomException(ErrorCode.PARTICIPANT_NOT_FOUND));
    }
}
