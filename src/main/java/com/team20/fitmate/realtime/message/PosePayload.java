package com.team20.fitmate.realtime.message;

import tools.jackson.databind.JsonNode;

/**
 * 동작 정보(Pose) 내용. 클라이언트가 POSE로 보내고, 서버가 PARTICIPANT_POSE로 같은 방에 전달한다.
 * <p>
 * <b>동작 정보는 DB에 저장하지 않는다.</b> 수행 기록에도 넣지 않고, 서버 메모리에도 남기지 않는다.
 * 받은 즉시 같은 방의 다른 참가자에게 보내고 버린다(이슈 #4, {@code docs/동작정보_형식.md}).
 * <p>
 * 서버는 좌표를 해석하거나 고치지 않는다. 관절 개수·순서·좌표계는 AI·3D 담당이 정하므로,
 * 여기서는 중계해도 되는 <b>모양</b>인지만 본다(관절 배열이고, 관절 하나가 숫자 3~4개).
 * 형식이 정해지기 전이라도 서버를 고치지 않고 받을 수 있게 하려는 것이다.
 *
 * @param landmarks 관절 배열. 관절 하나는 {@code [x, y, z]} 또는 {@code [x, y, z, visibility]}
 */
public record PosePayload(JsonNode landmarks) {

    /** 관절 최대 개수. MediaPipe Pose 기준 한 사람 33개 */
    public static final int MAX_LANDMARKS = 33;

    /** 관절 하나의 최소 값 개수 (x, y, z) */
    public static final int MIN_VALUES_PER_LANDMARK = 3;

    /** 관절 하나의 최대 값 개수 (x, y, z, visibility) */
    public static final int MAX_VALUES_PER_LANDMARK = 4;

    /**
     * 클라이언트가 보낸 payload에서 관절 배열만 꺼내 중계할 내용을 만든다.
     * <p>
     * 관절 배열 외의 필드는 버린다. 다른 참가자에게 서버가 확인하지 않은 값이 섞여 나가지 않게 하기 위해서다.
     *
     * @return 모양이 맞으면 중계할 내용, 틀리면 null
     */
    public static PosePayload from(JsonNode payload) {
        if (payload == null || !payload.isObject()) {
            return null;
        }
        JsonNode landmarks = payload.path("landmarks");
        return hasValidShape(landmarks) ? new PosePayload(landmarks) : null;
    }

    /**
     * 관절 배열의 모양만 확인한다. 값의 범위나 의미는 보지 않는다.
     * <ul>
     *     <li>배열이고 관절이 1개 이상 {@value #MAX_LANDMARKS}개 이하</li>
     *     <li>관절 하나가 숫자 {@value #MIN_VALUES_PER_LANDMARK}~{@value #MAX_VALUES_PER_LANDMARK}개의 배열</li>
     * </ul>
     * getter로 직렬화되지 않도록 is·get으로 시작하지 않는 이름을 쓴다.
     */
    static boolean hasValidShape(JsonNode landmarks) {
        if (landmarks == null || !landmarks.isArray()
                || landmarks.isEmpty() || landmarks.size() > MAX_LANDMARKS) {
            return false;
        }
        for (JsonNode landmark : landmarks) {
            if (!landmark.isArray()
                    || landmark.size() < MIN_VALUES_PER_LANDMARK || landmark.size() > MAX_VALUES_PER_LANDMARK) {
                return false;
            }
            for (JsonNode value : landmark) {
                if (!value.isNumber()) {
                    return false;
                }
            }
        }
        return true;
    }
}
