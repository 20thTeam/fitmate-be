package com.team20.fitmate.realtime.room;

/**
 * 실시간 방 하나를 가리키는 값. 친구 파티의 운동방·공개 파티·챌린지 판은 식별값이 겹칠 수 있어 종류와 함께 묶는다.
 *
 * @param type 방 종류
 * @param id   종류 안에서의 식별값(운동방·공개 파티·챌린지 판 식별값)
 */
public record SpaceKey(SpaceType type, Long id) {

    /** 친구 파티 운동방 */
    public static SpaceKey room(Long roomId) {
        return new SpaceKey(SpaceType.ROOM, roomId);
    }

    /** 공개 파티 */
    public static SpaceKey publicParty(Long partyId) {
        return new SpaceKey(SpaceType.PUBLIC_PARTY, partyId);
    }

    /** 학교 대항 스쿼트 챌린지 판(이슈 #34) */
    public static SpaceKey contestGame(Long gameId) {
        return new SpaceKey(SpaceType.CONTEST_GAME, gameId);
    }

    /** 친구 파티 운동방인지 */
    public boolean isRoom() {
        return type == SpaceType.ROOM;
    }

    /** 공개 파티인지 */
    public boolean isPublicParty() {
        return type == SpaceType.PUBLIC_PARTY;
    }

    /** 챌린지 판인지 */
    public boolean isContestGame() {
        return type == SpaceType.CONTEST_GAME;
    }

    /**
     * 실시간 방 종류.
     */
    public enum SpaceType {

        /** 친구 2~5명이 함께하는 지정 운동방. 정원이 있다 */
        ROOM,

        /** 시간이 정해진 공개 선셋 운동장(이슈 #11). 정원이 없다 */
        PUBLIC_PARTY,

        /** 학교 대항 스쿼트 챌린지 판(이슈 #34). 정원은 판의 maxPlayers(4명)이고, 판이 끝나면 연결을 정리한다 */
        CONTEST_GAME
    }
}
