package com.team20.fitmate.realtime.room;

/**
 * 실시간 방 하나를 가리키는 값. 친구 파티의 운동방과 공개 파티는 식별값이 겹칠 수 있어 종류와 함께 묶는다.
 *
 * @param type 방 종류
 * @param id   종류 안에서의 식별값(운동방 식별값 또는 공개 파티 식별값)
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

    /** 친구 파티 운동방인지 */
    public boolean isRoom() {
        return type == SpaceType.ROOM;
    }

    /**
     * 실시간 방 종류.
     */
    public enum SpaceType {

        /** 친구 2~5명이 함께하는 지정 운동방. 정원이 있다 */
        ROOM,

        /** 시간이 정해진 공개 선셋 운동장(이슈 #11). 정원이 없다 */
        PUBLIC_PARTY
    }
}
