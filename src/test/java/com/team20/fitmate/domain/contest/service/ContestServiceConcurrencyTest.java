package com.team20.fitmate.domain.contest.service;

import com.team20.fitmate.domain.contest.ContestTestClock;
import com.team20.fitmate.domain.contest.dto.ContestGameResponse;
import com.team20.fitmate.domain.contest.dto.ContestJoinRequest;
import com.team20.fitmate.domain.contest.dto.ContestJoinResponse;
import com.team20.fitmate.domain.contest.repository.ContestGameRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 여러 노트북이 동시에 join·start를 눌러도 판 정원과 시작 시각이 하나로 정해지는지 확인하는 테스트.
 * <p>
 * 요청마다 별도 스레드·트랜잭션으로 실제 커밋해야 하므로 테스트 트랜잭션을 쓰지 않고, 끝나면 판을 지운다.
 */
@SpringBootTest
@Import(ContestTestClock.class)
class ContestServiceConcurrencyTest {

    @Autowired
    private ContestService contestService;

    @Autowired
    private ContestGameRepository gameRepository;

    @Autowired
    private ContestTestClock.MutableClock clock;

    @BeforeEach
    void setUp() {
        gameRepository.deleteAll();
        clock.set(ContestTestClock.BASE);
    }

    @AfterEach
    void tearDown() {
        gameRepository.deleteAll();
    }

    @Test
    @DisplayName("8명이 동시에 join하면 4명씩 두 판으로 나뉜다")
    void concurrentJoinFillsGamesOfFour() throws Exception {
        List<ContestJoinResponse> responses = runConcurrently(8,
                i -> () -> contestService.join(new ContestJoinRequest("참가자" + i, "seoultech")));

        Map<String, Long> playersPerGame = responses.stream()
                .collect(Collectors.groupingBy(response -> response.game().gameId(), Collectors.counting()));
        assertThat(playersPerGame).hasSize(2);
        assertThat(playersPerGame.values()).containsOnly(4L);
        assertThat(responses).extracting(ContestJoinResponse::playerId).doesNotHaveDuplicates();
    }

    @Test
    @DisplayName("4명이 동시에 start를 눌러도 startAt은 하나로 정해진다")
    void concurrentStartSetsSingleStartAt() throws Exception {
        List<ContestJoinResponse> joined = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            joined.add(contestService.join(new ContestJoinRequest("참가자" + i, "khu")));
        }
        Long gameId = Long.valueOf(joined.get(0).game().gameId());

        List<ContestGameResponse> responses = runConcurrently(4, i -> () -> {
            // 누를 때마다 서버 시각이 조금씩 달라도 처음 정한 startAt이 유지돼야 한다
            clock.advance(Duration.ofMillis(300));
            return contestService.start(gameId, Long.valueOf(joined.get(i).playerId()));
        });

        assertThat(responses).extracting(ContestGameResponse::startAt).containsOnly(responses.get(0).startAt());
        assertThat(contestService.getGame(gameId).startAt()).isEqualTo(responses.get(0).startAt());
    }

    /**
     * 작업 n개를 스레드 n개에서 같은 순간에 시작해 결과를 모은다.
     */
    private <T> List<T> runConcurrently(int count, Function<Integer, Callable<T>> taskFactory) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(count);
        CountDownLatch ready = new CountDownLatch(1);
        try {
            List<Future<T>> futures = new ArrayList<>();
            for (int i = 0; i < count; i++) {
                Callable<T> task = taskFactory.apply(i);
                futures.add(executor.submit(() -> {
                    ready.await();
                    return task.call();
                }));
            }
            ready.countDown();
            List<T> results = new ArrayList<>();
            for (Future<T> future : futures) {
                results.add(future.get());
            }
            return results;
        } finally {
            executor.shutdownNow();
        }
    }
}
