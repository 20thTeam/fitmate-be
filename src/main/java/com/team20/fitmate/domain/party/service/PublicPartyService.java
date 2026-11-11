package com.team20.fitmate.domain.party.service;

import com.team20.fitmate.domain.party.dto.PublicPartyResponse;
import com.team20.fitmate.domain.party.schedule.PublicPartyProperties;
import com.team20.fitmate.domain.party.schedule.PublicPartySchedule;
import com.team20.fitmate.global.exception.CustomException;
import com.team20.fitmate.global.exception.ErrorCode;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import java.time.Clock;

/**
 * 공개 파티(이슈 #11)의 오픈 상태를 설정값으로 계산하고, 실시간 입장 가능 여부를 판단한다.
 * <p>
 * 공개 파티는 DB에 저장하지 않는다. 식별값·시각은 설정({@code public-party.*})에서, 현재 인원은 실시간 계층에서 얻는다.
 * 운동 기록 저장은 데이터 담당(B)이 S7 운동 기록으로 처리한다.
 */
@Service
public class PublicPartyService {

    private final PublicPartyProperties properties;
    private final PublicPartySchedule schedule;
    private final Clock clock;
    private final ObjectProvider<PublicPartyOccupancy> occupancy;

    public PublicPartyService(PublicPartyProperties properties, Clock clock, ObjectProvider<PublicPartyOccupancy> occupancy) {
        this.properties = properties;
        this.schedule = new PublicPartySchedule(properties);
        this.clock = clock;
        this.occupancy = occupancy;
    }

    /**
     * 지금 공개 파티 상태를 조회한다. 닫혀 있으면 다음에 여는 시각을 함께 준다.
     */
    public PublicPartyResponse getCurrent() {
        PublicPartySchedule.PartyWindow window = schedule.windowAt(clock.instant());
        return PublicPartyResponse.of(properties.partyId(), properties.name(), window, countParticipants());
    }

    /**
     * 이 공개 파티에 지금 실시간으로 들어갈 수 있는지 확인한다.
     *
     * @throws CustomException 없는 파티면 {@link ErrorCode#PUBLIC_PARTY_NOT_FOUND},
     *                         닫혀 있으면 {@link ErrorCode#PUBLIC_PARTY_CLOSED}
     */
    public void validateJoinable(Long partyId) {
        if (!properties.partyId().equals(partyId)) {
            throw new CustomException(ErrorCode.PUBLIC_PARTY_NOT_FOUND);
        }
        if (!schedule.windowAt(clock.instant()).open()) {
            throw new CustomException(ErrorCode.PUBLIC_PARTY_CLOSED);
        }
    }

    /** 실시간 계층이 없으면(단독 테스트 등) 0명으로 본다 */
    private int countParticipants() {
        PublicPartyOccupancy counter = occupancy.getIfAvailable();
        return counter == null ? 0 : counter.countParticipants(properties.partyId());
    }
}
