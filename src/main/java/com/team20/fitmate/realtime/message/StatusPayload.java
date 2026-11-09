package com.team20.fitmate.realtime.message;

import com.team20.fitmate.realtime.room.ProgressStatus;

/**
 * 현재 운동·진행 상태. 클라이언트가 STATUS로 보내고, 서버가 PARTICIPANT_STATUS로 같은 방에 그대로 전달한다.
 * <p>
 * 실시간 서버는 이 값을 해석하지 않는다. 운동 이름이 루틴과 맞는지 확인하지 않고, 운동 기록도 만들지 않는다
 * (운동 기록 시작·종료는 프론트가 REST로 직접 호출한다 — 이슈 #7 합의).
 *
 * @param exerciseId     현재 운동 식별값. 아직 운동을 고르지 않았으면 null
 * @param exerciseName   현재 운동 이름(화면 표시용). 아직 없으면 null
 * @param progressStatus 진행 상태. 반드시 있어야 한다
 */
public record StatusPayload(Long exerciseId, String exerciseName, ProgressStatus progressStatus) {

    /** 운동 이름 최대 길이. 화면 표시용이라 넉넉히 둔다 */
    public static final int MAX_EXERCISE_NAME_LENGTH = 50;

    /**
     * 진행 상태가 있고, 운동 식별값·이름이 형식에 맞는지 확인한다.
     */
    public boolean isValid() {
        if (progressStatus == null) {
            return false;
        }
        if (exerciseId != null && exerciseId <= 0) {
            return false;
        }
        return exerciseName == null || exerciseName.length() <= MAX_EXERCISE_NAME_LENGTH;
    }
}
