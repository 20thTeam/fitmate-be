package com.team20.fitmate.domain.exercise.init;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.Map;

/**
 * 운동별 AI 가이드 시범 동작 참조 설정({@code exercise-guide.motions}).
 * <p>
 * 키는 운동 코드(예: SQUAT), 값은 3D 담당이 정한 시범 동작 참조(파일 경로·애니메이션 이름 등)다.
 * 적지 않은 운동은 시범 없음이다. 다른 운동의 시범으로 대신하지 않도록 기본값을 두지 않는다.
 *
 * @param motions 운동 코드별 시범 동작 참조
 */
@ConfigurationProperties(prefix = "exercise-guide")
public record ExerciseGuideProperties(Map<String, String> motions) {

    public ExerciseGuideProperties {
        motions = motions == null ? Map.of() : Map.copyOf(motions);
    }
}
