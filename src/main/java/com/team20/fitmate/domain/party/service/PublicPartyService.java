package com.team20.fitmate.domain.party.service;

import com.team20.fitmate.domain.party.dto.PartyScheduleRequest;
import com.team20.fitmate.domain.party.dto.PublicPartyResponse;
import com.team20.fitmate.domain.party.dto.PublicPartyResponse.PartyStatus;
import com.team20.fitmate.domain.party.entity.PartyMode;
import com.team20.fitmate.domain.party.entity.PublicPartySetting;
import com.team20.fitmate.domain.party.repository.PublicPartySettingRepository;
import com.team20.fitmate.domain.party.schedule.PublicPartyProperties;
import com.team20.fitmate.domain.party.schedule.PublicPartySchedule;
import com.team20.fitmate.domain.party.schedule.PublicPartySchedule.PartyWindow;
import com.team20.fitmate.global.exception.CustomException;
import com.team20.fitmate.global.exception.ErrorCode;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Optional;

/**
 * 공개 파티(이슈 #11)의 오픈 상태를 계산하고, 실시간 입장 가능 여부를 판단한다.
 * <p>
 * 열림 판단 기준은 운영 모드와 운영 시각이다(이슈 #37). 홈 카드 조회, 공개 파티 운동 기록 시작({@code 409-4}),
 * 실시간 JOIN 모두 {@link #getCurrent()}·{@link #validateJoinable(Long)}을 거쳐 같은 기준을 따른다.
 * <ul>
 *     <li>운영 모드는 메모리에만 둔다. 서버를 다시 시작하면 AUTO로 돌아간다.</li>
 *     <li>운영 시각은 운영 화면에서 바꾸면 DB({@link PublicPartySetting})에 저장하고, 없으면 설정값을 쓴다.</li>
 * </ul>
 * 식별값·이름은 설정({@code public-party.*})에서, 현재 인원은 실시간 계층에서 얻는다.
 */
@Service
public class PublicPartyService {

    private static final DateTimeFormatter HH_MM = DateTimeFormatter.ofPattern("HH:mm");

    private final PublicPartyProperties properties;
    private final PublicPartySettingRepository settingRepository;
    private final Clock clock;
    private final ObjectProvider<PublicPartyOccupancy> occupancy;

    /** 운영 모드와 OPEN으로 바꾼 시각. 둘을 함께 바꾸려고 한 객체로 둔다 */
    private volatile ModeState modeState = new ModeState(PartyMode.AUTO, null);

    public PublicPartyService(PublicPartyProperties properties, PublicPartySettingRepository settingRepository,
                              Clock clock, ObjectProvider<PublicPartyOccupancy> occupancy) {
        this.properties = properties;
        this.settingRepository = settingRepository;
        this.clock = clock;
        this.occupancy = occupancy;
    }

    /**
     * 지금 공개 파티 상태를 조회한다. 닫혀 있으면 다음에 여는 시각을 함께 준다.
     */
    @Transactional(readOnly = true)
    public PublicPartyResponse getCurrent() {
        return currentAt(clock.instant());
    }

    /**
     * 이 공개 파티에 지금 실시간으로 들어갈 수 있는지 확인한다.
     *
     * @throws CustomException 없는 파티면 {@link ErrorCode#PUBLIC_PARTY_NOT_FOUND},
     *                         닫혀 있으면 {@link ErrorCode#PUBLIC_PARTY_CLOSED}
     */
    @Transactional(readOnly = true)
    public void validateJoinable(Long partyId) {
        if (!properties.partyId().equals(partyId)) {
            throw new CustomException(ErrorCode.PUBLIC_PARTY_NOT_FOUND);
        }
        if (currentAt(clock.instant()).status() != PartyStatus.OPEN) {
            throw new CustomException(ErrorCode.PUBLIC_PARTY_CLOSED);
        }
    }

    /**
     * 운영 모드를 바꾼다. OPEN으로 바꾼 시각을 연 시각(opensAt)으로 기억하고, 이미 OPEN이면 그 시각을 그대로 둔다.
     * 닫아도 이미 들어와 있는 참가자를 내보내지 않고, 진행 중인 운동 기록은 끝까지 저장된다.
     */
    @Transactional(readOnly = true)
    public synchronized PublicPartyResponse changeMode(PartyMode mode) {
        Instant now = clock.instant();
        ModeState before = modeState;
        if (mode == PartyMode.OPEN) {
            modeState = new ModeState(mode, before.mode() == PartyMode.OPEN ? before.openedAt() : now);
        } else {
            modeState = new ModeState(mode, null);
        }
        return currentAt(now);
    }

    /**
     * 매일 여는 시각과 운영 시간을 바꾸고 DB에 저장한다. 운영 모드는 바꾸지 않는다.
     */
    @Transactional
    public PublicPartyResponse changeSchedule(PartyScheduleRequest request) {
        LocalTime openTime = request.openLocalTime();
        int durationMinutes = request.durationMinutes();
        settingRepository.findById(properties.partyId()).ifPresentOrElse(
                setting -> setting.change(openTime, durationMinutes),
                () -> settingRepository.save(PublicPartySetting.create(properties.partyId(), openTime, durationMinutes)));
        return currentAt(clock.instant());
    }

    /** 운영 모드와 운영 시각으로 now 시점 상태를 계산한다 */
    private PublicPartyResponse currentAt(Instant now) {
        ModeState state = modeState;
        Optional<PublicPartySetting> setting = settingRepository.findById(properties.partyId());
        LocalTime openTime = setting.map(PublicPartySetting::getOpenTime).orElseGet(properties::openLocalTime);
        int durationMinutes = setting.map(PublicPartySetting::getDurationMinutes).orElseGet(this::defaultDurationMinutes);
        PublicPartySchedule schedule = new PublicPartySchedule(properties.zoneId(), openTime,
                Duration.ofMinutes(durationMinutes), properties.openDays());

        PartyStatus status;
        OffsetDateTime opensAt;
        OffsetDateTime closesAt;
        switch (state.mode()) {
            case OPEN -> {
                status = PartyStatus.OPEN;
                opensAt = state.openedAt().atZone(properties.zoneId()).toOffsetDateTime();
                closesAt = null;
            }
            case CLOSED -> {
                PartyWindow next = schedule.nextWindowAfter(now);
                status = PartyStatus.CLOSED;
                opensAt = next.opensAt().toOffsetDateTime();
                closesAt = next.closesAt().toOffsetDateTime();
            }
            default -> {
                PartyWindow window = schedule.windowAt(now);
                status = window.open() ? PartyStatus.OPEN : PartyStatus.CLOSED;
                opensAt = window.opensAt().toOffsetDateTime();
                closesAt = window.closesAt().toOffsetDateTime();
            }
        }
        return new PublicPartyResponse(properties.partyId(), properties.name(), state.mode(), status,
                opensAt, closesAt, countParticipants(), openTime.format(HH_MM), durationMinutes);
    }

    /** 설정값(여는·닫는 시각)의 운영 시간(분). 운영 화면에서 바꾼 적이 없을 때 쓴다 */
    private int defaultDurationMinutes() {
        return (int) PublicPartySchedule
                .durationBetween(properties.openLocalTime(), properties.closeLocalTime()).toMinutes();
    }

    /** 실시간 계층이 없으면(단독 테스트 등) 0명으로 본다 */
    private int countParticipants() {
        PublicPartyOccupancy counter = occupancy.getIfAvailable();
        return counter == null ? 0 : counter.countParticipants(properties.partyId());
    }

    /**
     * 운영 모드 상태.
     *
     * @param mode     운영 모드
     * @param openedAt OPEN으로 바꾼 시각. OPEN이 아니면 null
     */
    private record ModeState(PartyMode mode, Instant openedAt) {
    }
}
