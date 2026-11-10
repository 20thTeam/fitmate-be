package com.team20.fitmate.realtime.room;

import com.team20.fitmate.realtime.message.ParticipantView;
import com.team20.fitmate.realtime.message.StatusPayload;
import lombok.AccessLevel;
import lombok.Getter;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;

/**
 * 운동방 안 참가자 한 명의 현재 상태. 메모리에만 있고 DB에 저장하지 않는다.
 * <p>
 * 스레드 안전하지 않다. {@link RoomSessionRegistry}의 잠금 안에서만 읽고 바꾼다.
 */
@Getter
class RoomPresence {

    /** 동작 정보 빈도 상한을 세는 창의 길이 */
    static final Duration POSE_WINDOW = Duration.ofSeconds(1);

    /** 참가자 식별값 */
    private final Long participantId;

    /** 닉네임 */
    private final String nickname;

    /** 캐릭터 코드 */
    private final String characterCode;

    /** 현재 운동 식별값. 없으면 null */
    private Long exerciseId;

    /** 현재 운동 이름. 없으면 null */
    private String exerciseName;

    /** 진행 상태 */
    private ProgressStatus progressStatus = ProgressStatus.WAITING;

    /** 연결 상태 */
    private ConnectionStatus connectionStatus = ConnectionStatus.CONNECTED;

    /** 지금 이 참가자를 대표하는 연결 식별값. 연결 불안정이면 null */
    private String sessionId;

    /** 연결 불안정이 된 시각. 연결됨이면 null */
    private Instant unstableSince;

    /**
     * 최근 1초 안에 중계한 동작 정보의 시각(오래된 것부터). 빈도 상한 계산에만 쓴다.
     * 동작 정보 내용은 담지 않는다.
     */
    @Getter(AccessLevel.NONE)
    private final Deque<Instant> recentPoseTimes = new ArrayDeque<>();

    RoomPresence(JoinTicket ticket, String sessionId) {
        this.participantId = ticket.participantId();
        this.nickname = ticket.nickname();
        this.characterCode = ticket.characterCode();
        this.sessionId = sessionId;
    }

    /**
     * 새 연결로 다시 붙는다(재접속 또는 다른 창). 진행 상태는 그대로 둔다.
     */
    void reconnect(String newSessionId) {
        this.sessionId = newSessionId;
        this.connectionStatus = ConnectionStatus.CONNECTED;
        this.unstableSince = null;
    }

    /**
     * 연결이 끊겨 재접속을 기다리는 상태로 바꾼다. 마지막 진행 상태는 지우지 않지만 현재로 단정하지 않도록 표시한다.
     */
    void markUnstable(Instant now) {
        this.sessionId = null;
        this.connectionStatus = ConnectionStatus.UNSTABLE;
        this.unstableSince = now;
    }

    /**
     * 클라이언트가 보낸 현재 운동·진행 상태로 바꾼다.
     */
    void updateStatus(StatusPayload status) {
        this.exerciseId = status.exerciseId();
        this.exerciseName = status.exerciseName();
        this.progressStatus = status.progressStatus();
    }

    /**
     * 동작 정보를 하나 더 중계해도 되는지 확인하고, 되면 지금 시각을 기록한다.
     * <p>
     * 최근 {@link #POSE_WINDOW} 안에 중계한 수가 {@code maxPerWindow}보다 적을 때만 허용한다(움직이는 1초 창).
     * 네트워크 때문에 메시지가 몰려 와도 1초 평균이 상한 안이면 버리지 않는다.
     *
     * @return 중계해도 되면 true, 상한을 넘어 버려야 하면 false
     */
    boolean tryAcquirePoseSlot(Instant now, int maxPerWindow) {
        Instant windowStart = now.minus(POSE_WINDOW);
        while (!recentPoseTimes.isEmpty() && !recentPoseTimes.peekFirst().isAfter(windowStart)) {
            recentPoseTimes.pollFirst();
        }
        if (recentPoseTimes.size() >= maxPerWindow) {
            return false;
        }
        recentPoseTimes.addLast(now);
        return true;
    }

    /**
     * 재접속 대기 시간이 지났는지 확인한다.
     */
    boolean isReconnectExpired(Instant now, Duration grace) {
        return connectionStatus == ConnectionStatus.UNSTABLE
                && unstableSince != null
                && !now.isBefore(unstableSince.plus(grace));
    }

    /**
     * 화면 표시용 값으로 바꾼다.
     */
    ParticipantView toView() {
        return new ParticipantView(participantId, nickname, characterCode,
                exerciseId, exerciseName, progressStatus, connectionStatus);
    }

    /**
     * 현재 운동·진행 상태만 담은 값으로 바꾼다.
     */
    StatusPayload toStatus() {
        return new StatusPayload(exerciseId, exerciseName, progressStatus);
    }
}
