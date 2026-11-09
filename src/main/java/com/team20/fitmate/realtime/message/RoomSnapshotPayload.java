package com.team20.fitmate.realtime.message;

import java.util.List;

/**
 * ROOM_SNAPSHOT 내용. 입장(또는 재접속)한 본인에게 방 전체 상태를 한 번에 알려 준다.
 *
 * @param capacity     정원(동시 접속 인원)
 * @param participants 본인을 포함한 현재 참가자 목록(입장 순서). 연결 불안정인 참가자도 재접속 대기 중이면 포함한다
 */
public record RoomSnapshotPayload(int capacity, List<ParticipantView> participants) {
}
