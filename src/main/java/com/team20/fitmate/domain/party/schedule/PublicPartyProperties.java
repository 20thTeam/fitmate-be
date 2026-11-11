package com.team20.fitmate.domain.party.schedule;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.DateTimeException;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * 공개 파티(공개 선셋 에어로빅, 이슈 #11) 설정. {@code application.yaml}의 {@code public-party.*}에서 읽는다.
 * <p>
 * <b>오픈·종료 시각, 운영 요일, 기준 시간대는 모두 임시값이다.</b> PM이 확정하면 설정만 바꾼다.
 * 기획서의 "밤 10시쯤"을 코드에 박아 넣지 않으려고 설정으로 뺐다.
 * <p>
 * 종료 시각이 오픈 시각보다 이르면(예: 22:00 ~ 00:30) 자정을 넘겨 다음 날 종료 시각에 닫는 것으로 본다.
 *
 * @param partyId   공개 파티 식별값. 실시간 JOIN의 {@code partyId}로 쓴다. DB 테이블 없이 설정으로만 둔다
 * @param name      홈 카드에 보여줄 이름
 * @param zone      오픈·종료 시각의 기준 시간대(IANA 이름, 예: Asia/Seoul)
 * @param openTime  매일 여는 시각(HH:mm)
 * @param closeTime 매일 닫는 시각(HH:mm). 여는 시각과 같으면 안 된다
 * @param days      여는 요일(MONDAY ~ SUNDAY). 비워 두면 매일 연다. 자정을 넘기는 경우 여는 시각의 요일 기준이다
 */
@ConfigurationProperties(prefix = "public-party")
public record PublicPartyProperties(
        @DefaultValue("1") Long partyId,
        @DefaultValue("선셋 에어로빅") String name,
        @DefaultValue("Asia/Seoul") String zone,
        @DefaultValue("22:00") String openTime,
        @DefaultValue("23:00") String closeTime,
        List<String> days
) {

    /**
     * 시각·시간대·요일 형식을 앱 시작 때 확인한다. 틀리면 실행하지 않는다(잘못된 설정으로 열린 것처럼 보이지 않게).
     */
    public PublicPartyProperties {
        days = days == null ? List.of() : List.copyOf(days);
        if (partyId == null || partyId <= 0) {
            throw new IllegalArgumentException("public-party.party-id는 1 이상이어야 합니다.");
        }
        try {
            ZoneId.of(zone);
            LocalTime open = LocalTime.parse(openTime);
            LocalTime close = LocalTime.parse(closeTime);
            if (open.equals(close)) {
                throw new IllegalArgumentException("public-party.open-time과 close-time이 같습니다.");
            }
            days.forEach(day -> DayOfWeek.valueOf(day.trim().toUpperCase(Locale.ROOT)));
        } catch (DateTimeException e) {
            throw new IllegalArgumentException("public-party 시간대·시각 형식이 올바르지 않습니다.", e);
        }
    }

    /** 기준 시간대 */
    public ZoneId zoneId() {
        return ZoneId.of(zone);
    }

    /** 여는 시각 */
    public LocalTime openLocalTime() {
        return LocalTime.parse(openTime);
    }

    /** 닫는 시각 */
    public LocalTime closeLocalTime() {
        return LocalTime.parse(closeTime);
    }

    /** 여는 요일. 설정이 비어 있으면 매일 */
    public Set<DayOfWeek> openDays() {
        if (days.isEmpty()) {
            return EnumSet.allOf(DayOfWeek.class);
        }
        EnumSet<DayOfWeek> result = EnumSet.noneOf(DayOfWeek.class);
        days.forEach(day -> result.add(DayOfWeek.valueOf(day.trim().toUpperCase(Locale.ROOT))));
        return result;
    }
}
