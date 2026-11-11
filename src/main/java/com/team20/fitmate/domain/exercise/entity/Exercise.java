package com.team20.fitmate.domain.exercise.entity;

import com.team20.fitmate.global.common.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 운동. 스쿼트·플랭크처럼 인식 가능한 동작 하나다.
 * <p>
 * {@code code}는 추천 규칙이 운동을 가리킬 때 쓰는 고정 이름이다.
 * DB에서 자동으로 매기는 {@code id}는 환경마다 달라질 수 있어 규칙에 쓰지 않는다.
 */
@Getter
@Entity
@Table(name = "exercise")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Exercise extends BaseTimeEntity {

    /** 운동 ID */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 추천 규칙에서 쓰는 고정 코드 (예: SQUAT) */
    @Column(name = "code", nullable = false, unique = true, length = 30)
    private String code;

    /** 화면에 보여줄 운동 이름 */
    @Column(name = "name", nullable = false, length = 30)
    private String name;

    /** 측정 방식 (횟수형·시간형) */
    @Enumerated(EnumType.STRING)
    @Column(name = "measure_type", nullable = false, length = 10)
    private MeasureType measureType;

    /** AI 가이드 시범 동작 참조 (3D 담당이 정한 값). 없으면 시범 없음이다. 설정값 {@code exercise-guide.motions}에서 채운다 */
    @Column(name = "guide_motion_ref")
    private String guideMotionRef;

    /** 카메라 인식이 검증된 운동인지. 검증된 운동만 추천에 쓴다 */
    @Column(name = "recognition_verified", nullable = false)
    private boolean recognitionVerified;

    private Exercise(String code, String name, MeasureType measureType, boolean recognitionVerified) {
        this.code = code;
        this.name = name;
        this.measureType = measureType;
        this.recognitionVerified = recognitionVerified;
    }

    /**
     * 새 운동을 만든다. 초기 데이터 적재에서 쓴다.
     */
    public static Exercise create(String code, String name, MeasureType measureType, boolean recognitionVerified) {
        return new Exercise(code, name, measureType, recognitionVerified);
    }

    /**
     * 가이드 시범 동작 참조를 바꾼다. 빈 값이면 시범 없음(null)으로 둔다.
     */
    public void changeGuideMotionRef(String guideMotionRef) {
        this.guideMotionRef = guideMotionRef == null || guideMotionRef.isBlank() ? null : guideMotionRef.strip();
    }

    /**
     * 가이드 시범이 있는지 본다.
     */
    public boolean hasGuideMotion() {
        return guideMotionRef != null;
    }
}
