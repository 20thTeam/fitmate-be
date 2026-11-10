package com.team20.fitmate.realtime.message;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link PosePayload}가 관절 배열의 모양만 검사하고, 관절 배열 외의 값은 중계하지 않는지 확인한다.
 */
class PosePayloadTest {

    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    private static JsonNode json(String text) {
        return MAPPER.readTree(text);
    }

    /** 관절 n개짜리 동작 정보 payload를 만든다 */
    private static String landmarks(int count) {
        StringBuilder builder = new StringBuilder("{\"landmarks\":[");
        for (int i = 0; i < count; i++) {
            builder.append(i == 0 ? "" : ",").append("[0.1,0.2,0.3,0.9]");
        }
        return builder.append("]}").toString();
    }

    @ParameterizedTest(name = "관절 {0}개는 받는다")
    @ValueSource(ints = {1, 13, 33})
    @DisplayName("관절 1~33개는 받는다")
    void acceptsLandmarkCount(int count) {
        assertThat(PosePayload.from(json(landmarks(count)))).isNotNull();
    }

    @ParameterizedTest(name = "관절 {0}개는 거절한다")
    @ValueSource(ints = {0, 34})
    @DisplayName("관절이 없거나 33개를 넘으면 거절한다")
    void rejectsLandmarkCount(int count) {
        assertThat(PosePayload.from(json(landmarks(count)))).isNull();
    }

    @Test
    @DisplayName("관절 하나는 숫자 3개(x,y,z) 또는 4개(visibility 포함)여야 한다")
    void valuesPerLandmark() {
        assertThat(PosePayload.from(json("{\"landmarks\":[[0.1,0.2,0.3]]}"))).isNotNull();
        assertThat(PosePayload.from(json("{\"landmarks\":[[0.1,0.2,0.3,0.9]]}"))).isNotNull();
        assertThat(PosePayload.from(json("{\"landmarks\":[[0.1,0.2]]}"))).isNull();
        assertThat(PosePayload.from(json("{\"landmarks\":[[0.1,0.2,0.3,0.9,1]]}"))).isNull();
    }

    @Test
    @DisplayName("숫자가 아닌 값, 배열이 아닌 관절, landmarks 누락은 거절한다")
    void rejectsWrongShape() {
        assertThat(PosePayload.from(json("{\"landmarks\":[[\"0.1\",0.2,0.3]]}"))).isNull();
        assertThat(PosePayload.from(json("{\"landmarks\":[[0.1,null,0.3]]}"))).isNull();
        assertThat(PosePayload.from(json("{\"landmarks\":[{\"x\":0.1,\"y\":0.2,\"z\":0.3}]}"))).isNull();
        assertThat(PosePayload.from(json("{\"landmarks\":\"none\"}"))).isNull();
        assertThat(PosePayload.from(json("{}"))).isNull();
        assertThat(PosePayload.from(json("[]"))).isNull();
        assertThat(PosePayload.from(null)).isNull();
    }

    @Test
    @DisplayName("관절 배열 외의 필드는 중계 내용에 넣지 않는다")
    void dropsExtraFields() {
        PosePayload pose = PosePayload.from(json("{\"landmarks\":[[0.1,0.2,0.3]],\"participantId\":999,\"note\":\"x\"}"));

        String relayed = MAPPER.writeValueAsString(pose);

        assertThat(relayed).isEqualTo("{\"landmarks\":[[0.1,0.2,0.3]]}");
    }
}
