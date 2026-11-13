package com.team20.fitmate.domain.party.repository;

import com.team20.fitmate.domain.party.entity.PublicPartySetting;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 공개 파티 운영 시각 저장소.
 */
public interface PublicPartySettingRepository extends JpaRepository<PublicPartySetting, Long> {
}
