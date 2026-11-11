package com.team20.fitmate.domain.party.schedule;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Set;

/**
 * 설정값(여는 시각·닫는 시각·요일·시간대)으로 지금 공개 파티가 열려 있는지와 그 시간 구간을 계산한다.
 * <p>
 * DB나 시계에 기대지 않는 순수 계산이라 시각을 넣어 바로 테스트할 수 있다.
 * 여는 시각은 포함하고 닫는 시각은 포함하지 않는다(22:00:00에 열리고 23:00:00에 닫힘).
 */
public class PublicPartySchedule {

    /** 다음 오픈을 찾을 때 살펴볼 최대 일수. 요일이 하나만 있어도 8일 안에는 반드시 나온다 */
    private static final int MAX_LOOKAHEAD_DAYS = 8;

    private final ZoneId zone;
    private final LocalTime openTime;
    private final LocalTime closeTime;
    private final Set<DayOfWeek> openDays;

    public PublicPartySchedule(PublicPartyProperties properties) {
        this(properties.zoneId(), properties.openLocalTime(), properties.closeLocalTime(), properties.openDays());
    }

    public PublicPartySchedule(ZoneId zone, LocalTime openTime, LocalTime closeTime, Set<DayOfWeek> openDays) {
        this.zone = zone;
        this.openTime = openTime;
        this.closeTime = closeTime;
        this.openDays = Set.copyOf(openDays);
    }

    /**
     * 지금 열려 있으면 지금 구간을, 닫혀 있으면 다음에 열릴 구간을 돌려준다.
     */
    public PartyWindow windowAt(Instant now) {
        LocalDate today = now.atZone(zone).toLocalDate();
        // 자정을 넘기는 구간은 어제 연 것이 아직 열려 있을 수 있어 어제부터 본다
        for (int offset = -1; offset <= MAX_LOOKAHEAD_DAYS; offset++) {
            LocalDate day = today.plusDays(offset);
            if (!openDays.contains(day.getDayOfWeek())) {
                continue;
            }
            ZonedDateTime opensAt = day.atTime(openTime).atZone(zone);
            ZonedDateTime closesAt = closesAfter(day).atTime(closeTime).atZone(zone);
            Instant start = opensAt.toInstant();
            Instant end = closesAt.toInstant();
            if (!now.isBefore(start) && now.isBefore(end)) {
                return new PartyWindow(true, opensAt, closesAt);
            }
            if (now.isBefore(start)) {
                return new PartyWindow(false, opensAt, closesAt);
            }
        }
        throw new IllegalStateException("공개 파티 다음 오픈 시각을 찾지 못했습니다.");
    }

    /** 닫는 날짜. 닫는 시각이 여는 시각보다 이르면 자정을 넘겨 다음 날 닫는다 */
    private LocalDate closesAfter(LocalDate openDay) {
        return closeTime.isAfter(openTime) ? openDay : openDay.plusDays(1);
    }

    /**
     * 공개 파티 시간 구간 하나.
     *
     * @param open     지금 열려 있는지
     * @param opensAt  이 구간이 열리는 시각(열려 있으면 이미 지난 시각)
     * @param closesAt 이 구간이 닫히는 시각
     */
    public record PartyWindow(boolean open, ZonedDateTime opensAt, ZonedDateTime closesAt) {
    }
}
