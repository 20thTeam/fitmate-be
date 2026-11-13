package com.team20.fitmate.domain.party.schedule;

import com.team20.fitmate.domain.party.schedule.PublicPartySchedule.PartyWindow;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.EnumSet;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 공개 파티 오픈 시간 계산을 시각을 넣어 확인한다. 기준 시간대는 Asia/Seoul(UTC+9)이다.
 * 2026-11-11은 수요일이다.
 */
class PublicPartyScheduleTest {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

    /** 매일 22:00 ~ 23:00 (서울) */
    private final PublicPartySchedule daily = new PublicPartySchedule(
            SEOUL, LocalTime.of(22, 0), LocalTime.of(23, 0), EnumSet.allOf(DayOfWeek.class));

    /** 서울 시각을 Instant로 바꾼다 */
    private static Instant seoul(String localDateTime) {
        return OffsetDateTime.parse(localDateTime + "+09:00").toInstant();
    }

    @Test
    @DisplayName("오픈 시각 전이면 닫혀 있고 오늘 오픈·종료 시각을 알려 준다")
    void closedBeforeOpen() {
        PartyWindow window = daily.windowAt(seoul("2026-11-11T21:59:59"));

        assertThat(window.open()).isFalse();
        assertThat(window.opensAt().toOffsetDateTime()).isEqualTo(OffsetDateTime.parse("2026-11-11T22:00+09:00"));
        assertThat(window.closesAt().toOffsetDateTime()).isEqualTo(OffsetDateTime.parse("2026-11-11T23:00+09:00"));
    }

    @Test
    @DisplayName("오픈 시각 정각부터 열리고, 종료 시각 정각에는 닫힌다")
    void openIncludesStartExcludesEnd() {
        assertThat(daily.windowAt(seoul("2026-11-11T22:00:00")).open()).isTrue();
        assertThat(daily.windowAt(seoul("2026-11-11T22:59:59")).open()).isTrue();

        PartyWindow atClose = daily.windowAt(seoul("2026-11-11T23:00:00"));
        assertThat(atClose.open()).isFalse();
        assertThat(atClose.opensAt().toOffsetDateTime()).isEqualTo(OffsetDateTime.parse("2026-11-12T22:00+09:00"));
    }

    @Test
    @DisplayName("기준 시간대로 계산한다: 서울 22:30은 UTC 13:30이다")
    void usesConfiguredZone() {
        assertThat(daily.windowAt(Instant.parse("2026-11-11T13:30:00Z")).open()).isTrue();
        assertThat(daily.windowAt(Instant.parse("2026-11-11T22:30:00Z")).open()).isFalse();
    }

    @Test
    @DisplayName("종료 시각이 오픈 시각보다 이르면 자정을 넘겨 다음 날 종료 시각에 닫는다")
    void overnightWindow() {
        PublicPartySchedule overnight = new PublicPartySchedule(
                SEOUL, LocalTime.of(22, 0), LocalTime.of(0, 30), EnumSet.allOf(DayOfWeek.class));

        PartyWindow afterMidnight = overnight.windowAt(seoul("2026-11-12T00:10:00"));

        assertThat(afterMidnight.open()).isTrue();
        assertThat(afterMidnight.opensAt().toOffsetDateTime()).isEqualTo(OffsetDateTime.parse("2026-11-11T22:00+09:00"));
        assertThat(afterMidnight.closesAt().toOffsetDateTime()).isEqualTo(OffsetDateTime.parse("2026-11-12T00:30+09:00"));
        assertThat(overnight.windowAt(seoul("2026-11-12T00:30:00")).open()).isFalse();
    }

    @Test
    @DisplayName("여는 요일이 아니면 닫혀 있고 다음 여는 요일의 시각을 알려 준다")
    void openDaysOnly() {
        PublicPartySchedule fridayOnly = new PublicPartySchedule(
                SEOUL, LocalTime.of(22, 0), LocalTime.of(23, 0), EnumSet.of(DayOfWeek.FRIDAY));

        PartyWindow wednesdayNight = fridayOnly.windowAt(seoul("2026-11-11T22:30:00"));

        assertThat(wednesdayNight.open()).isFalse();
        assertThat(wednesdayNight.opensAt().toOffsetDateTime()).isEqualTo(OffsetDateTime.parse("2026-11-13T22:00+09:00"));
        assertThat(fridayOnly.windowAt(seoul("2026-11-13T22:30:00")).open()).isTrue();
    }

    @Test
    @DisplayName("자정을 넘기는 구간의 요일은 여는 시각 기준이다 (금요일 22:00에 열어 토요일 00:30에 닫음)")
    void overnightUsesOpeningDay() {
        PublicPartySchedule fridayOvernight = new PublicPartySchedule(
                SEOUL, LocalTime.of(22, 0), LocalTime.of(0, 30), EnumSet.of(DayOfWeek.FRIDAY));

        assertThat(fridayOvernight.windowAt(seoul("2026-11-14T00:10:00")).open()).isTrue();
        assertThat(fridayOvernight.windowAt(seoul("2026-11-15T00:10:00")).open()).isFalse();
    }

    @Test
    @DisplayName("요일을 비우면 매일 열고, 시각·시간대·요일 형식이 틀리거나 오픈과 종료가 같으면 앱이 시작하지 않는다")
    void propertiesValidation() {
        PublicPartyProperties properties = new PublicPartyProperties(1L, "선셋", "Asia/Seoul", "22:00", "23:00", null);
        assertThat(properties.openDays()).hasSize(7);
        assertThat(new PublicPartyProperties(1L, "선셋", "Asia/Seoul", "22:00", "23:00", List.of("friday")).openDays())
                .containsExactly(DayOfWeek.FRIDAY);

        assertThatThrownBy(() -> new PublicPartyProperties(1L, "선셋", "Asia/Seoul", "22시", "23:00", null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new PublicPartyProperties(1L, "선셋", "Mars/Base", "22:00", "23:00", null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new PublicPartyProperties(1L, "선셋", "Asia/Seoul", "22:00", "22:00", null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new PublicPartyProperties(1L, "선셋", "Asia/Seoul", "22:00", "23:00", List.of("FUNDAY")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new PublicPartyProperties(0L, "선셋", "Asia/Seoul", "22:00", "23:00", null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("운영 시간(분)으로 만들면 자정을 넘겨 닫는다: 23:30부터 90분이면 00:50에 열려 있고 01:00에 닫힌다")
    void durationOverMidnight() {
        PublicPartySchedule schedule = new PublicPartySchedule(
                SEOUL, LocalTime.of(23, 30), Duration.ofMinutes(90), EnumSet.allOf(DayOfWeek.class));

        PartyWindow window = schedule.windowAt(seoul("2026-11-12T00:50:00"));

        assertThat(window.open()).isTrue();
        assertThat(window.opensAt().toOffsetDateTime()).isEqualTo(OffsetDateTime.parse("2026-11-11T23:30+09:00"));
        assertThat(window.closesAt().toOffsetDateTime()).isEqualTo(OffsetDateTime.parse("2026-11-12T01:00+09:00"));
        assertThat(schedule.windowAt(seoul("2026-11-12T01:00:00")).open()).isFalse();
    }

    @Test
    @DisplayName("다음 구간은 지금 열려 있으면 그다음 날, 닫혀 있으면 오늘 구간이다")
    void nextWindowAfter() {
        assertThat(daily.nextWindowAfter(seoul("2026-11-11T22:30:00")).opensAt().toOffsetDateTime())
                .isEqualTo(OffsetDateTime.parse("2026-11-12T22:00+09:00"));
        assertThat(daily.nextWindowAfter(seoul("2026-11-11T21:00:00")).opensAt().toOffsetDateTime())
                .isEqualTo(OffsetDateTime.parse("2026-11-11T22:00+09:00"));
    }

    @Test
    @DisplayName("운영 시간이 0이거나 하루 이상이면 만들 수 없다")
    void invalidDuration() {
        assertThatThrownBy(() -> new PublicPartySchedule(
                SEOUL, LocalTime.of(22, 0), Duration.ZERO, EnumSet.allOf(DayOfWeek.class)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new PublicPartySchedule(
                SEOUL, LocalTime.of(22, 0), Duration.ofDays(1), EnumSet.allOf(DayOfWeek.class)))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
