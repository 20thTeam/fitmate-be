package com.team20.fitmate.realtime.room;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

/**
 * 실시간 연결의 시간·크기 설정. {@code application.yaml}의 {@code realtime.*}에서 읽는다.
 * <p>
 * <b>모두 임시값이다.</b> 다중 PC 테스트 후 개발팀이 제안하고 PM이 승인하면 바꾼다(미정 사항 임시값 처리 정책).
 *
 * @param joinTimeout      연결 후 JOIN을 기다리는 시간. 넘으면 JOIN_TIMEOUT으로 닫는다
 * @param heartbeatTimeout 마지막 메시지 이후 이 시간 동안 아무 메시지가 없으면 연결 불안정으로 본다
 * @param reconnectGrace   연결 불안정 상태로 재접속을 기다리는 시간. 넘으면 퇴장(TIMEOUT) 처리한다
 * @param sendTimeLimit    한 연결로 보내는 메시지가 이 시간 이상 막히면 그 연결을 닫는다
 * @param sendBufferSize   한 연결에 쌓아 둘 수 있는 보낼 메시지 크기(바이트). 넘으면 그 연결을 닫는다
 * @param poseMaxBytes     동작 정보(POSE) 메시지 하나의 최대 크기(바이트). 넘으면 INVALID_MESSAGE로 거절한다
 * @param poseMaxPerSecond 참가자 한 명이 1초 동안 중계할 수 있는 동작 정보 수. 넘는 메시지는 조용히 버린다
 * @param cheerCooldown    같은 친구에게 응원을 다시 보낼 수 있을 때까지의 시간. 그 안의 응원은 연타로 보고 무시한다
 */
@ConfigurationProperties(prefix = "realtime")
public record RealtimeProperties(
        @DefaultValue("10s") Duration joinTimeout,
        @DefaultValue("30s") Duration heartbeatTimeout,
        @DefaultValue("30s") Duration reconnectGrace,
        @DefaultValue("5s") Duration sendTimeLimit,
        @DefaultValue("65536") int sendBufferSize,
        @DefaultValue("2048") int poseMaxBytes,
        @DefaultValue("15") int poseMaxPerSecond,
        @DefaultValue("1s") Duration cheerCooldown
) {
}
