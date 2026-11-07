package com.team20.fitmate.global.common;

import jakarta.persistence.Column;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.MappedSuperclass;
import lombok.Getter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

/**
 * 모든 엔티티가 공통으로 갖는 생성·수정 시각 컬럼.
 * <p>
 * 엔티티가 이 클래스를 상속하면 저장·수정 시점에 JPA Auditing이 값을 자동으로 채운다.
 * Auditing 활성화는 {@code JpaAuditingConfig}에서 한다.
 */
@Getter
@MappedSuperclass
@EntityListeners(AuditingEntityListener.class)
public abstract class BaseTimeEntity {

    /** 처음 저장된 시각. 이후 바뀌지 않는다 */
    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /** 마지막으로 수정된 시각 */
    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
