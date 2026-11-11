package com.team20.fitmate.realtime.message;

import java.util.List;

/**
 * ROOM_SNAPSHOT 내용. 입장(또는 재접속)한 본인에게 방 전체 상태를 한 번에 알려 준다.
 *
 * @param capacity     정원(동시 접속 인원). 공개 파티처럼 정원이 없으면 null
 * @param participants 본인을 포함한 현재 참가자 목록(입장 순서). 연결 불안정인 참가자도 재접속 대기 중이면 포함한다
 * @param reconnected  이미 방에 자리가 있던 참가자가 새 연결로 돌아온 것이면 true(재접속·새 창), 처음 입장이면 false.
 *                     true면 목록 안의 본인 진행 상태는 끊기기 전 마지막 값을 그대로 복원한 것이다(이슈 #7).
 *                     프론트는 이 값으로 "다시 연결됐어요" 안내를 띄우고, 본인의 현재 상태를 STATUS로 한 번 다시 보낸다
 */
public record RoomSnapshotPayload(Integer capacity, List<ParticipantView> participants, boolean reconnected) {
}
