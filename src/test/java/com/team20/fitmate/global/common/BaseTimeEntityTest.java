package com.team20.fitmate.global.common;

import com.team20.fitmate.global.config.JpaAuditingConfig;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityManager;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import lombok.Getter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link BaseTimeEntity}를 상속한 엔티티를 저장하면
 * JPA Auditing이 생성·수정 시각을 자동으로 채우는지 확인하는 테스트.
 */
@DataJpaTest
@Import(JpaAuditingConfig.class)
class BaseTimeEntityTest {

    @Autowired
    private EntityManager em;

    @Test
    @DisplayName("엔티티를 저장하면 createdAt과 updatedAt이 채워진다")
    void auditing() {
        AuditingTestEntity entity = new AuditingTestEntity();

        em.persist(entity);
        em.flush();

        assertThat(entity.getCreatedAt()).isNotNull();
        assertThat(entity.getUpdatedAt()).isNotNull();
    }

    /**
     * 테스트에서만 쓰는 엔티티. BaseTimeEntity 상속만 확인한다.
     */
    @Getter
    @Entity
    static class AuditingTestEntity extends BaseTimeEntity {

        /** 식별값 */
        @Id
        @GeneratedValue
        private Long id;
    }
}
