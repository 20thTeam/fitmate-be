package com.team20.fitmate.domain.contest.repository;

import com.team20.fitmate.domain.contest.entity.ContestGame;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

/**
 * 학교 대항 스쿼트 챌린지 판 저장소.
 */
public interface ContestGameRepository extends JpaRepository<ContestGame, Long> {

    /**
     * 아직 시작하지 않은 판을 오래된 순으로 찾는다. join이 이 중 자리가 남은 첫 판에 넣는다.
     */
    @Query("select g from ContestGame g where g.startAt is null order by g.id asc")
    List<ContestGame> findWaitingGames();

    /**
     * 시작을 누른 판을 참가자와 함께 모두 읽는다. 현황(standings) 집계에서 끝난 판을 골라 쓴다.
     */
    @Query("select distinct g from ContestGame g left join fetch g.players where g.startAt is not null")
    List<ContestGame> findStartedGamesWithPlayers();

    /**
     * 판 참가자·결과를 모두 지운다(운영 초기화, 이슈 #41). 판보다 먼저 지워야 외래 키에 걸리지 않는다.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from ContestPlayer")
    int deleteAllPlayers();

    /**
     * 판을 모두 지운다(운영 초기화, 이슈 #41). 진행 중인 판도 지운다.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from ContestGame")
    int deleteAllGames();
}
