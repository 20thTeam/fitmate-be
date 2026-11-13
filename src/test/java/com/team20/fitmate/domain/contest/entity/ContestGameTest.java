package com.team20.fitmate.domain.contest.entity;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 판 상태 계산(시간 경과·제출 여부)과 시작·결과 규칙을 DB 없이 확인하는 테스트.
 */
class ContestGameTest {

    private static final Instant T0 = Instant.parse("2026-11-13T05:00:00Z");

    @Test
    @DisplayName("시작 전 WAITING, startAt 전 COUNTDOWN, startAt부터 RUNNING, startAt+30초부터 FINISHED")
    void statusByTime() {
        ContestGame game = gameWithPlayers(2);
        assertThat(game.statusAt(T0)).isEqualTo(ContestGameStatus.WAITING);

        game.start(T0);
        Instant startAt = T0.plusSeconds(5);
        assertThat(game.getStartAt()).isEqualTo(startAt);
        assertThat(game.statusAt(T0)).isEqualTo(ContestGameStatus.COUNTDOWN);
        assertThat(game.statusAt(startAt.minusMillis(1))).isEqualTo(ContestGameStatus.COUNTDOWN);
        assertThat(game.statusAt(startAt)).isEqualTo(ContestGameStatus.RUNNING);
        // 운동이 끝난 뒤(20초)에도 제출 대기 10초 동안은 RUNNING
        assertThat(game.statusAt(startAt.plusSeconds(25))).isEqualTo(ContestGameStatus.RUNNING);
        assertThat(game.statusAt(startAt.plusSeconds(30).minusMillis(1))).isEqualTo(ContestGameStatus.RUNNING);
        assertThat(game.statusAt(startAt.plusSeconds(30))).isEqualTo(ContestGameStatus.FINISHED);
    }

    @Test
    @DisplayName("모든 참가자가 결과를 내면 마감 전이라도 FINISHED")
    void finishedWhenAllSubmitted() {
        ContestGame game = gameWithPlayers(2);
        game.start(T0);
        Instant running = T0.plusSeconds(10);

        game.getPlayers().get(0).submitResult(12);
        assertThat(game.statusAt(running)).isEqualTo(ContestGameStatus.RUNNING);

        game.getPlayers().get(1).submitResult(null);
        assertThat(game.statusAt(running)).isEqualTo(ContestGameStatus.FINISHED);
    }

    @Test
    @DisplayName("이미 시작된 판을 다시 시작해도 startAt은 처음 값 그대로다")
    void startIsIdempotent() {
        ContestGame game = gameWithPlayers(1);
        assertThat(game.start(T0)).isTrue();
        assertThat(game.start(T0.plusSeconds(3))).isFalse();
        assertThat(game.getStartAt()).isEqualTo(T0.plusSeconds(5));
    }

    @Test
    @DisplayName("결과를 두 번 내면 처음 값이 남는다. null(미측정)도 처음 값이면 그대로 남는다")
    void submitKeepsFirstValue() {
        ContestGame game = gameWithPlayers(2);
        ContestPlayer first = game.getPlayers().get(0);
        assertThat(first.submitResult(15)).isTrue();
        assertThat(first.submitResult(30)).isFalse();
        assertThat(first.getReps()).isEqualTo(15);

        ContestPlayer second = game.getPlayers().get(1);
        second.submitResult(null);
        second.submitResult(7);
        assertThat(second.getReps()).isNull();
        assertThat(second.isSubmitted()).isTrue();
        assertThat(second.hasMeasuredResult()).isFalse();
    }

    @Test
    @DisplayName("4명이 차거나 시작하면 더 들어올 수 없고, 시작 후에는 나가기가 무시된다")
    void joinAndLeaveRules() {
        ContestGame game = gameWithPlayers(3);
        assertThat(game.canJoin()).isTrue();
        addPlayer(game, 4L);
        assertThat(game.canJoin()).isFalse();

        ContestGame started = gameWithPlayers(2);
        started.start(T0);
        assertThat(started.canJoin()).isFalse();
        assertThat(started.removePlayerBeforeStart(1L)).isFalse();
        assertThat(started.getPlayers()).hasSize(2);

        ContestGame waiting = gameWithPlayers(2);
        assertThat(waiting.removePlayerBeforeStart(1L)).isTrue();
        assertThat(waiting.removePlayerBeforeStart(1L)).isFalse();
        assertThat(waiting.getPlayers()).hasSize(1);
    }

    /**
     * 식별값 1..n을 가진 참가자 n명이 들어온 판을 만든다 (DB 없이 식별값을 직접 넣는다).
     */
    private ContestGame gameWithPlayers(int count) {
        ContestGame game = ContestGame.create();
        for (long id = 1; id <= count; id++) {
            addPlayer(game, id);
        }
        return game;
    }

    private void addPlayer(ContestGame game, long id) {
        ContestPlayer player = game.addPlayer("참가자" + id, "seoultech");
        ReflectionTestUtils.setField(player, "id", id);
    }
}
