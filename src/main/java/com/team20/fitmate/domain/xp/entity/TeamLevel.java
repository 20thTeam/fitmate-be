package com.team20.fitmate.domain.xp.entity;

/**
 * 팀 누적 경험치로 정하는 팀 레벨 (PM 확정, 2026-11-09). 레벨은 저장하지 않고 누적 경험치로 매번 계산한다.
 * <ul>
 *     <li>Lv.1: 0~99</li>
 *     <li>Lv.2: 100~299</li>
 *     <li>Lv.3: 300~549</li>
 *     <li>Lv.4: 550~849</li>
 *     <li>Lv.5: 850 이상 (최고 레벨)</li>
 * </ul>
 *
 * @param level       레벨 (1부터)
 * @param levelMinXp  지금 레벨의 시작 경험치
 * @param nextLevelXp 다음 레벨의 시작 경험치. 최고 레벨이면 null
 */
public record TeamLevel(int level, int levelMinXp, Integer nextLevelXp) {

    /** 레벨별 시작 경험치. 인덱스 0이 Lv.1 */
    private static final int[] LEVEL_MIN_XP = {0, 100, 300, 550, 850};

    /**
     * 누적 경험치에 해당하는 레벨을 구한다.
     */
    public static TeamLevel of(int totalXp) {
        int index = 0;
        while (index + 1 < LEVEL_MIN_XP.length && totalXp >= LEVEL_MIN_XP[index + 1]) {
            index++;
        }
        Integer next = index + 1 < LEVEL_MIN_XP.length ? LEVEL_MIN_XP[index + 1] : null;
        return new TeamLevel(index + 1, LEVEL_MIN_XP[index], next);
    }

    /** 최고 레벨인지 */
    public boolean isMax() {
        return nextLevelXp == null;
    }
}
