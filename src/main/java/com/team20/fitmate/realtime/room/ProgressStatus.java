package com.team20.fitmate.realtime.room;

/**
 * 참가자의 개인 운동 진행 상태. 이슈 #7의 공통 계약(대기, 진행, 전환·휴식, 종료, 카메라 오류)을 따른다.
 * <p>
 * 이 값은 화면 표시용으로만 같은 방에 전달한다. 다른 참가자의 상태가 내 진행을 바꾸는 경로는 만들지 않는다.
 */
public enum ProgressStatus {

    /** 입장 후 운동 시작 전 대기 (입장 직후 기본값) */
    WAITING,

    /** 운동 진행 중 */
    EXERCISING,

    /** 운동 간 전환·휴식 중 */
    RESTING,

    /** 루틴을 마침 */
    FINISHED,

    /** 카메라 권한 거부·장치 오류로 인식할 수 없음 */
    CAMERA_ERROR
}
