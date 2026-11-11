package com.team20.fitmate.domain.exercise.init;

import com.team20.fitmate.domain.exercise.entity.Exercise;
import com.team20.fitmate.domain.exercise.entity.MeasureType;
import com.team20.fitmate.domain.exercise.repository.ExerciseRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 서버 시작 때 운동 초기 데이터를 넣는다. 이미 있는 코드는 건너뛰므로 여러 번 실행해도 안전하다.
 * <p>
 * <b>임시 목록이다.</b> 매니패스트 와이어프레임 예시(스쿼트·힙 힌지 데드리프트·플랭크)에 런지를 더했다.
 * 운동 목록과 인식 가능 여부는 PM·AI가 확정한다.
 * 확정되면 이 목록과 {@code recognitionVerified} 값을 맞춘다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ExerciseDataInitializer implements ApplicationRunner {

    /**
     * 초기 운동 목록 (임시). 프론트 자세 인식(fitmate-fe {@code lib/pose/repCounter.ts})이 셀 수 있는 운동이다.
     * 서버가 이미 떠 있던 DB에도 없는 운동만 추가된다.
     */
    private static final List<ExerciseSeed> INITIAL_EXERCISES = List.of(
            new ExerciseSeed("SQUAT", "스쿼트", MeasureType.COUNT, true),
            new ExerciseSeed("HIP_HINGE_DEADLIFT", "힙 힌지 데드리프트", MeasureType.COUNT, true),
            new ExerciseSeed("LUNGE", "런지", MeasureType.COUNT, true),
            new ExerciseSeed("PLANK", "플랭크", MeasureType.TIME, true),
            new ExerciseSeed("JUMPING_JACK", "점핑잭", MeasureType.COUNT, true),
            new ExerciseSeed("HIGH_KNEES", "하이 니", MeasureType.COUNT, true)
    );

    private final ExerciseRepository exerciseRepository;
    private final ExerciseGuideProperties guideProperties;

    /**
     * 아직 없는 운동만 저장하고, 모든 운동의 가이드 시범 참조를 설정값과 맞춘다.
     * 설정에서 빠진 운동은 시범 없음(null)으로 되돌린다.
     */
    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        List<Exercise> missing = INITIAL_EXERCISES.stream()
                .filter(seed -> !exerciseRepository.existsByCode(seed.code()))
                .map(seed -> Exercise.create(seed.code(), seed.name(), seed.measureType(), seed.recognitionVerified()))
                .toList();
        exerciseRepository.saveAll(missing);
        log.info("운동 초기 데이터 {}건 적재", missing.size());
        applyGuideMotions();
    }

    /**
     * 설정값의 가이드 시범 참조를 운동에 반영한다. 없는 운동 코드는 경고만 남긴다.
     */
    private void applyGuideMotions() {
        Map<String, String> motions = guideProperties.motions();
        List<Exercise> exercises = exerciseRepository.findAll();
        exercises.forEach(exercise -> exercise.changeGuideMotionRef(motions.get(exercise.getCode())));
        Set<String> knownCodes = exercises.stream().map(Exercise::getCode).collect(Collectors.toSet());
        motions.keySet().stream()
                .filter(code -> !knownCodes.contains(code))
                .forEach(code -> log.warn("가이드 시범 설정에 없는 운동 코드: {}", code));
        log.info("가이드 시범 참조 {}건 반영", exercises.stream().filter(Exercise::hasGuideMotion).count());
    }

    /**
     * 초기 운동 하나의 정의.
     *
     * @param code                추천 규칙에서 쓰는 고정 코드
     * @param name                운동 이름
     * @param measureType         측정 방식
     * @param recognitionVerified 인식 검증 여부
     */
    private record ExerciseSeed(String code, String name, MeasureType measureType, boolean recognitionVerified) {
    }
}
