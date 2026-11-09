package com.team20.fitmate.realtime.message;

import com.team20.fitmate.realtime.room.ConnectionStatus;
import com.team20.fitmate.realtime.room.ProgressStatus;

/**
 * 화면에 그릴 참가자 한 명의 상태. ROOM_SNAPSHOT 목록과 PARTICIPANT_JOINED에 쓴다.
 *
 * @param participantId    참가자 식별값. 닉네임은 중복될 수 있으므로 구분은 이 값으로 한다
 * @param nickname         닉네임
 * @param characterCode    캐릭터 코드
 * @param exerciseId       현재 운동 식별값. 없으면 null
 * @param exerciseName     현재 운동 이름. 없으면 null
 * @param progressStatus   진행 상태
 * @param connectionStatus 연결 상태
 */
public record ParticipantView(
        Long participantId,
        String nickname,
        String characterCode,
        Long exerciseId,
        String exerciseName,
        ProgressStatus progressStatus,
        ConnectionStatus connectionStatus
) {
}
