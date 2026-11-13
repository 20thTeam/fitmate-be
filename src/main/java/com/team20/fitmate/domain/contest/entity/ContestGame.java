package com.team20.fitmate.domain.contest.entity;

import com.team20.fitmate.global.common.BaseTimeEntity;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 학교 대항 스쿼트 20초 챌린지의 판 하나 (이슈 #34, 시연장 이벤트 {@code /univ-contest}).
 * <p>
 * 시연장 노트북 최대 4대가 같은 판에 들어와 동시에 20초 스쿼트를 하고 횟수를 낸다.
 * 운동방·공개 파티와 별개이며 참가자 토큰 없이 쓴다.
 * 상태는 저장하지 않고 {@link #statusAt(Instant)}로 시작 시각·제출 여부·현재 시각에서 계산한다.
 */
@Getter
@Entity
@Table(name = "univ_contest_game")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ContestGame extends BaseTimeEntity {

    /** 한 판에 들어올 수 있는 최대 인원 (시연장 노트북 4대) */
    public static final int MAX_PLAYERS = 4;

    /** 운동 시간(초) */
    public static final int DURATION_SECONDS = 20;

    /** 시작을 누른 뒤 운동이 시작되기까지의 카운트다운 */
    public static final Duration COUNTDOWN = Duration.ofSeconds(5);

    /** 운동이 끝난 뒤 결과 제출을 더 기다리는 시간. 이 시간이 지나면 안 낸 사람은 미측정으로 남는다 */
    public static final Duration SUBMIT_GRACE = Duration.ofSeconds(10);

    /** 판 식별값 */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 운동 시작 시각(카운트다운이 끝나는 순간). 시작 전이면 null이고, 한 번 정해지면 바뀌지 않는다 */
    @Column(name = "start_at")
    private Instant startAt;

    /** 판에 들어온 사람들. 들어온 순서대로 둔다 */
    @OneToMany(mappedBy = "game", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id asc")
    private List<ContestPlayer> players = new ArrayList<>();

    /**
     * 모집 중인 새 판을 만든다.
     */
    public static ContestGame create() {
        return new ContestGame();
    }

    /**
     * 판에 한 사람을 넣는다. 정원 확인은 판을 고르는 서비스가 한다.
     */
    public ContestPlayer addPlayer(String nickname, String schoolId) {
        ContestPlayer player = new ContestPlayer(this, nickname, schoolId);
        players.add(player);
        return player;
    }

    /**
     * 판에서 한 사람을 뺀다. 시작 전에만 뺀다. 시작 후거나 판에 없는 사람이면 아무것도 하지 않는다.
     *
     * @return 실제로 뺐으면 true
     */
    public boolean removePlayerBeforeStart(Long playerId) {
        if (isStarted()) {
            return false;
        }
        return players.removeIf(player -> player.getId().equals(playerId));
    }

    /**
     * 판을 시작한다. 운동 시작 시각은 {@code now + 5초}다.
     * 이미 시작된 판이면 시작 시각을 바꾸지 않는다(여러 명이 동시에 눌러도 처음 정한 값 하나만 남는다).
     *
     * @return 이번 호출로 시작했으면 true, 이미 시작돼 있었으면 false
     */
    public boolean start(Instant now) {
        if (isStarted()) {
            return false;
        }
        this.startAt = now.plus(COUNTDOWN);
        return true;
    }

    /**
     * 시작을 누른 판인지 확인한다.
     */
    public boolean isStarted() {
        return startAt != null;
    }

    /**
     * 판에 들어온 사람을 식별값으로 찾는다.
     */
    public Optional<ContestPlayer> findPlayer(Long playerId) {
        return players.stream().filter(player -> player.getId().equals(playerId)).findFirst();
    }

    /**
     * 모집 중이고 자리가 남아 새로 들어올 수 있는 판인지 확인한다.
     */
    public boolean canJoin() {
        return !isStarted() && players.size() < MAX_PLAYERS;
    }

    /**
     * 주어진 시각 기준 판 상태를 계산한다.
     * <ul>
     *     <li>시작 전 → WAITING</li>
     *     <li>startAt 전 → COUNTDOWN</li>
     *     <li>모두 결과를 냈거나 startAt+20초+10초가 지남 → FINISHED</li>
     *     <li>그 밖 → RUNNING (운동 중이거나 결과 제출 대기 중)</li>
     * </ul>
     */
    public ContestGameStatus statusAt(Instant now) {
        if (!isStarted()) {
            return ContestGameStatus.WAITING;
        }
        if (now.isBefore(startAt)) {
            return ContestGameStatus.COUNTDOWN;
        }
        if (allSubmitted() || !now.isBefore(submitDeadline())) {
            return ContestGameStatus.FINISHED;
        }
        return ContestGameStatus.RUNNING;
    }

    /**
     * 결과 제출을 받는 마지막 시각(startAt + 20초 + 10초). 시작 전이면 null.
     */
    public Instant submitDeadline() {
        return isStarted() ? startAt.plusSeconds(DURATION_SECONDS).plus(SUBMIT_GRACE) : null;
    }

    /**
     * 판에 있는 모든 사람이 결과를 냈는지 확인한다. 아무도 없으면 false.
     */
    private boolean allSubmitted() {
        return !players.isEmpty() && players.stream().allMatch(ContestPlayer::isSubmitted);
    }
}
