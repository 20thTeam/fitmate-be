package com.team20.fitmate.domain.workout.entity;

/**
 * 운동 하나의 측정 상태. 측정하지 못한 값을 0으로 채우지 않기 위해 값과 별도로 보낸다.
 */
public enum MeasurementStatus {

    /**
     * 측정됨. 카메라 판정 결과가 있다.
     * 횟수형은 유효 횟수, 시간형은 실제 소요 시간이 반드시 있어야 한다. 0회도 측정된 값이다.
     */
    MEASURED,

    /**
     * 측정 못 함. 권한 거부, 장치 없음, 인식 불가, 연결 끊김 등으로 판정 근거가 없다.
     * 유효 횟수·판정 횟수·자세 결과는 비워 두고, 지나간 시간만 보낼 수 있다.
     */
    NOT_MEASURED
}
