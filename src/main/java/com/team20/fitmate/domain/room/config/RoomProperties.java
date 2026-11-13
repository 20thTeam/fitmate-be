package com.team20.fitmate.domain.room.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 운동방 설정({@code room.*}). 모두 시연용 임시값이다(이슈 #36).
 *
 * @param seedCount 서버 시작 때 미리 만들어 둘 시연용 운동방 수(지정 운동방 제외)
 * @param maxCount  서버 전체 운동방 수 상한. 넘으면 방 만들기를 409로 막는다
 * @param capacity  새로 만드는 운동방 정원(동시 접속 인원)
 */
@ConfigurationProperties(prefix = "room")
public record RoomProperties(int seedCount, int maxCount, int capacity) {
}
