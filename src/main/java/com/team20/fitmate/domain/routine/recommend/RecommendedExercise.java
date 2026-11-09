package com.team20.fitmate.domain.routine.recommend;

/**
 * 추천 규칙이 고른 운동 하나.
 *
 * @param exerciseCode 운동 코드 (예: SQUAT)
 * @param amount       횟수형이면 목표 횟수, 시간형이면 목표 시간(초)
 * @param sets         세트 수
 */
public record RecommendedExercise(String exerciseCode, int amount, int sets) {
}
