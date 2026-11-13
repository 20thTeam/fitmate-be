package com.team20.fitmate.domain.contest.dto;

/**
 * 실시간 입장(/ws JOIN) 확인용 판 참가 정보 (이슈 #34). 실시간 계층이 엔티티 대신 이 값만 들고 간다.
 *
 * @param gameId     판 식별값
 * @param playerId   판 참가 식별값
 * @param nickname   화면에 보일 이름
 * @param maxPlayers 판 정원
 * @param finished   판이 이미 끝났는지(FINISHED)
 */
public record ContestSeat(Long gameId, Long playerId, String nickname, int maxPlayers, boolean finished) {
}
