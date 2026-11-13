package com.team20.fitmate.domain.party.entity;

import com.team20.fitmate.global.common.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalTime;

/**
 * 운영 화면에서 바꾼 공개 파티 운영 시각(이슈 #37). 시연 당일 재배포해도 유지되도록 DB에 둔다.
 * <p>
 * 행이 없으면 설정값({@code public-party.open-time}·{@code close-time})을 쓴다.
 * 식별값은 공개 파티 식별값({@code public-party.party-id})과 같다. 시연 데이터 초기화로 지워지지 않는다.
 */
@Getter
@Entity
@Table(name = "public_party_setting")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PublicPartySetting extends BaseTimeEntity {

    /** 공개 파티 식별값 */
    @Id
    private Long id;

    /** 매일 여는 시각(기준 시간대) */
    @Column(name = "open_time", nullable = false)
    private LocalTime openTime;

    /** 열어 두는 시간(분) */
    @Column(name = "duration_minutes", nullable = false)
    private int durationMinutes;

    private PublicPartySetting(Long id, LocalTime openTime, int durationMinutes) {
        this.id = id;
        this.openTime = openTime;
        this.durationMinutes = durationMinutes;
    }

    /**
     * 공개 파티 운영 시각을 새로 만든다.
     */
    public static PublicPartySetting create(Long partyId, LocalTime openTime, int durationMinutes) {
        return new PublicPartySetting(partyId, openTime, durationMinutes);
    }

    /**
     * 운영 시각을 바꾼다.
     */
    public void change(LocalTime openTime, int durationMinutes) {
        this.openTime = openTime;
        this.durationMinutes = durationMinutes;
    }
}
