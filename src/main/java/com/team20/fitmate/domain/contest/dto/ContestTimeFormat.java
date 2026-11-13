package com.team20.fitmate.domain.contest.dto;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

/**
 * 챌린지 응답의 시각 문자열 형식. 항상 UTC·밀리초 세 자리로 맞춘다 (예: {@code 2026-11-13T05:00:00.000Z}).
 * <p>
 * {@link Instant#toString()}은 밀리초가 0이면 생략해 자릿수가 들쭉날쭉하므로 형식을 고정한다.
 */
final class ContestTimeFormat {

    /** UTC 기준 ISO 8601, 밀리초 세 자리 */
    private static final DateTimeFormatter FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'").withZone(ZoneOffset.UTC);

    private ContestTimeFormat() {
    }

    /**
     * 시각을 문자열로 바꾼다. null이면 null.
     */
    static String format(Instant instant) {
        return instant == null ? null : FORMATTER.format(instant);
    }
}
