package com.team20.fitmate.domain.xp.entity;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link TeamLevel}의 PM 확정 레벨 구간을 확인하는 테스트.
 */
class TeamLevelTest {

    @ParameterizedTest(name = "{0}XP → Lv.{1}")
    @DisplayName("누적 경험치로 레벨과 다음 레벨 시작 경험치를 구한다")
    @CsvSource({
            "0, 1, 0, 100",
            "99, 1, 0, 100",
            "100, 2, 100, 300",
            "299, 2, 100, 300",
            "300, 3, 300, 550",
            "549, 3, 300, 550",
            "550, 4, 550, 850",
            "849, 4, 550, 850"
    })
    void levels(int totalXp, int level, int levelMinXp, int nextLevelXp) {
        TeamLevel teamLevel = TeamLevel.of(totalXp);

        assertThat(teamLevel.level()).isEqualTo(level);
        assertThat(teamLevel.levelMinXp()).isEqualTo(levelMinXp);
        assertThat(teamLevel.nextLevelXp()).isEqualTo(nextLevelXp);
        assertThat(teamLevel.isMax()).isFalse();
    }

    @ParameterizedTest(name = "{0}XP → Lv.5 (최고)")
    @DisplayName("850XP 이상은 최고 레벨 Lv.5이고 다음 레벨이 없다")
    @CsvSource({"850", "5000"})
    void maxLevel(int totalXp) {
        TeamLevel teamLevel = TeamLevel.of(totalXp);

        assertThat(teamLevel.level()).isEqualTo(5);
        assertThat(teamLevel.nextLevelXp()).isNull();
        assertThat(teamLevel.isMax()).isTrue();
    }
}
