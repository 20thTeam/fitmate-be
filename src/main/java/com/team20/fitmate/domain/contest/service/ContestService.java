package com.team20.fitmate.domain.contest.service;

import com.team20.fitmate.domain.contest.dto.ContestGameResponse;
import com.team20.fitmate.domain.contest.dto.ContestJoinRequest;
import com.team20.fitmate.domain.contest.dto.ContestJoinResponse;
import com.team20.fitmate.domain.contest.dto.ContestStandingsResponse;
import com.team20.fitmate.domain.contest.entity.ContestGame;
import com.team20.fitmate.domain.contest.entity.ContestGameStatus;
import com.team20.fitmate.domain.contest.entity.ContestPlayer;
import com.team20.fitmate.domain.contest.repository.ContestGameRepository;
import com.team20.fitmate.global.exception.CustomException;
import com.team20.fitmate.global.exception.ErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;

/**
 * 학교 대항 스쿼트 20초 챌린지 서비스 (이슈 #34). 판 참가·시작·결과·나가기와 현황 집계를 맡는다.
 * <p>
 * <b>동시 요청:</b> 시연장 노트북 4대가 거의 동시에 join·start를 누른다. 판 고르기(정원 4명)와 시작 시각 정하기가
 * 겹치지 않도록 바꾸는 요청은 한 줄로 세워 처리한다. 잠금은 트랜잭션 바깥에서 잡고 커밋이 끝난 뒤 푼다
 * (안쪽에서 풀면 다음 요청이 아직 커밋되지 않은 값을 못 본다). 서버가 EC2 한 대라 프로세스 안 잠금으로 충분하다.
 * 요청 수가 적어(노트북 4대, 초당 몇 건) 한 줄로 세워도 느려지지 않는다.
 * <p>
 * 상태는 저장하지 않고 {@link Clock} 기준 현재 시각으로 매번 계산한다.
 */
@Service
public class ContestService {

    /** 현황에 보여줄 한 판 기록 수 */
    static final int TOP_PLAYER_LIMIT = 5;

    private final ContestGameRepository gameRepository;
    private final Clock clock;
    private final TransactionTemplate transactionTemplate;

    /** 판 상태를 바꾸는 요청(join·start·result·leave)을 한 줄로 세우는 잠금 */
    private final ReentrantLock writeLock = new ReentrantLock(true);

    public ContestService(ContestGameRepository gameRepository, Clock clock, PlatformTransactionManager transactionManager) {
        this.gameRepository = gameRepository;
        this.clock = clock;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    /**
     * 판에 들어간다. 모집 중이고 4명 미만인 판 중 가장 오래된 판에 넣고, 없으면 새 판을 만든다.
     * 판이 가득 찼다고 오류를 내지 않는다.
     */
    public ContestJoinResponse join(ContestJoinRequest request) {
        return inWriteTransaction(() -> {
            ContestGame game = gameRepository.findWaitingGames().stream()
                    .filter(ContestGame::canJoin)
                    .findFirst()
                    .orElseGet(() -> gameRepository.save(ContestGame.create()));
            ContestPlayer player = game.addPlayer(request.nickname().strip(), request.schoolId().strip());
            // 판은 이미 영속 상태라 save(merge)하지 않는다. merge는 새 참가자를 복사본으로 저장해 이 객체에 식별값이 안 생긴다.
            // flush하면 판의 cascade로 새 참가자를 INSERT해 응답에 넣을 식별값이 생긴다
            gameRepository.flush();
            return new ContestJoinResponse(String.valueOf(player.getId()), ContestGameResponse.from(game, now()));
        });
    }

    /**
     * 판 상태를 조회한다.
     *
     * @throws CustomException 판이 없으면 {@link ErrorCode#CONTEST_GAME_NOT_FOUND}
     */
    @Transactional(readOnly = true)
    public ContestGameResponse getGame(Long gameId) {
        return ContestGameResponse.from(findGame(gameId), now());
    }

    /**
     * 판을 시작한다. 운동 시작 시각은 지금 + 5초다.
     * 이미 시작된 판이면 시작 시각을 바꾸지 않고 그대로 돌려준다(여러 명이 동시에 눌러도 결과가 같다).
     *
     * @throws CustomException 판이 없으면 {@link ErrorCode#CONTEST_GAME_NOT_FOUND},
     *                         그 판에 들어온 사람이 아니면 {@link ErrorCode#CONTEST_PLAYER_NOT_FOUND}
     */
    public ContestGameResponse start(Long gameId, Long playerId) {
        return inWriteTransaction(() -> {
            ContestGame game = findGame(gameId);
            requirePlayer(game, playerId);
            Instant now = now();
            game.start(now);
            return ContestGameResponse.from(game, now);
        });
    }

    /**
     * 한 판 결과를 낸다. 같은 사람이 두 번 내면 처음 값을 그대로 두고 성공으로 돌려준다(프론트 재전송 대비).
     * 판이 이미 끝났으면(제출 마감이 지났으면) 받지 않고 지금 상태를 돌려준다. 안 낸 사람은 미측정으로 남는다.
     *
     * @param reps 횟수. 측정 안 됨이면 null
     * @throws CustomException 판이 없으면 {@link ErrorCode#CONTEST_GAME_NOT_FOUND},
     *                         그 판에 들어온 사람이 아니면 {@link ErrorCode#CONTEST_PLAYER_NOT_FOUND},
     *                         아직 시작하지 않은 판이면 {@link ErrorCode#CONTEST_GAME_NOT_STARTED}
     */
    public ContestGameResponse submitResult(Long gameId, Long playerId, Integer reps) {
        return inWriteTransaction(() -> {
            ContestGame game = findGame(gameId);
            ContestPlayer player = requirePlayer(game, playerId);
            if (!game.isStarted()) {
                throw new CustomException(ErrorCode.CONTEST_GAME_NOT_STARTED);
            }
            Instant now = now();
            if (game.statusAt(now) != ContestGameStatus.FINISHED) {
                player.submitResult(reps);
            }
            return ContestGameResponse.from(game, now);
        });
    }

    /**
     * 시작 전에 판에서 나간다. 시작 후거나 이미 나간 사람이면 아무것도 하지 않고 지금 상태를 돌려준다.
     *
     * @throws CustomException 판이 없으면 {@link ErrorCode#CONTEST_GAME_NOT_FOUND}
     */
    public ContestGameResponse leave(Long gameId, Long playerId) {
        return inWriteTransaction(() -> {
            ContestGame game = findGame(gameId);
            game.removePlayerBeforeStart(playerId);
            gameRepository.flush();
            return ContestGameResponse.from(game, now());
        });
    }

    /**
     * 학교별 누적·상위 기록 현황을 집계한다. 끝난 판에서 측정된(reps가 null이 아닌) 기록만 쓴다.
     * <ul>
     *     <li>schools: 학교별 횟수 합계와 기록 수. 합계 큰 순, 같으면 학교 식별값 순</li>
     *     <li>topPlayers: 한 판 기록 상위 5개. 횟수 큰 순, 같으면 먼저 들어온 기록 순</li>
     *     <li>gameCount: 측정된 기록이 하나라도 있는 끝난 판 수</li>
     * </ul>
     */
    @Transactional(readOnly = true)
    public ContestStandingsResponse getStandings() {
        Instant now = now();
        List<ContestGame> finishedGames = gameRepository.findStartedGamesWithPlayers().stream()
                .filter(game -> game.statusAt(now) == ContestGameStatus.FINISHED)
                .toList();
        List<ContestPlayer> measured = finishedGames.stream()
                .flatMap(game -> game.getPlayers().stream())
                .filter(ContestPlayer::hasMeasuredResult)
                .toList();

        // 학교별 합계·기록 수
        Map<String, long[]> bySchool = new LinkedHashMap<>();
        for (ContestPlayer player : measured) {
            long[] sums = bySchool.computeIfAbsent(player.getSchoolId(), key -> new long[2]);
            sums[0] += player.getReps();
            sums[1]++;
        }
        List<ContestStandingsResponse.School> schools = bySchool.entrySet().stream()
                .map(entry -> new ContestStandingsResponse.School(entry.getKey(), entry.getValue()[0],
                        (int) entry.getValue()[1]))
                .sorted(Comparator.comparingLong(ContestStandingsResponse.School::totalReps).reversed()
                        .thenComparing(ContestStandingsResponse.School::schoolId))
                .toList();

        // 한 판 기록 상위 5개
        List<ContestStandingsResponse.TopPlayer> topPlayers = measured.stream()
                .sorted(Comparator.comparing(ContestPlayer::getReps).reversed()
                        .thenComparing(ContestPlayer::getId))
                .limit(TOP_PLAYER_LIMIT)
                .map(player -> new ContestStandingsResponse.TopPlayer(player.getNickname(), player.getSchoolId(),
                        player.getReps()))
                .toList();

        int gameCount = (int) finishedGames.stream()
                .filter(game -> game.getPlayers().stream().anyMatch(ContestPlayer::hasMeasuredResult))
                .count();
        return new ContestStandingsResponse(schools, topPlayers, gameCount);
    }

    /**
     * 판을 찾는다.
     */
    private ContestGame findGame(Long gameId) {
        return gameRepository.findById(gameId)
                .orElseThrow(() -> new CustomException(ErrorCode.CONTEST_GAME_NOT_FOUND));
    }

    /**
     * 판에 들어온 사람을 찾는다.
     */
    private ContestPlayer requirePlayer(ContestGame game, Long playerId) {
        return game.findPlayer(playerId)
                .orElseThrow(() -> new CustomException(ErrorCode.CONTEST_PLAYER_NOT_FOUND));
    }

    /**
     * 잠금을 잡은 채 트랜잭션 하나로 실행하고, 커밋이 끝난 뒤 잠금을 푼다.
     */
    private <T> T inWriteTransaction(Supplier<T> work) {
        writeLock.lock();
        try {
            return transactionTemplate.execute(status -> work.get());
        } finally {
            writeLock.unlock();
        }
    }

    /**
     * 현재 시각.
     */
    private Instant now() {
        return clock.instant();
    }
}
