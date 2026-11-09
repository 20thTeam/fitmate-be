package com.team20.fitmate.domain.workout.dto;

import com.team20.fitmate.domain.workout.entity.ExerciseRecord;
import com.team20.fitmate.domain.workout.entity.MeasurementStatus;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 프론트가 보낸 수행 기록 JSON을 {@link ExerciseRecordRequest}로 읽고 엔티티 값으로 바꾸는지 확인하는 테스트.
 */
class ExerciseRecordRequestTest {

    private final JsonMapper mapper = JsonMapper.builder().build();

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    @DisplayName("자세 결과는 어떤 JSON 형태든 그대로 받아 문자열로 바꾼다")
    void postureAsAnyJson() throws Exception {
        String json = """
                {"routineExerciseId":1,"measurementStatus":"MEASURED","validCount":10,"belowStandardCount":2,
                 "undeterminedCount":1,"actualSeconds":95,"performedSets":3,
                 "postureResult":{"feedback":["무릎이 발끝을 넘어요"],"score":null}}
                """;

        ExerciseRecordRequest request = mapper.readValue(json, ExerciseRecordRequest.class);
        ExerciseRecord.Values values = request.toValues();

        assertThat(values.measurementStatus()).isEqualTo(MeasurementStatus.MEASURED);
        assertThat(values.validCount()).isEqualTo(10);
        assertThat(mapper.readTree(values.postureResult()))
                .isEqualTo(mapper.readTree("{\"feedback\":[\"무릎이 발끝을 넘어요\"],\"score\":null}"));
    }

    @Test
    @DisplayName("자세 결과가 없거나 JSON null이면 결과 없음(null)으로 본다")
    void postureMissing() throws Exception {
        ExerciseRecordRequest missing = mapper.readValue(
                "{\"routineExerciseId\":1,\"measurementStatus\":\"NOT_MEASURED\"}", ExerciseRecordRequest.class);
        ExerciseRecordRequest explicitNull = mapper.readValue(
                "{\"routineExerciseId\":1,\"measurementStatus\":\"NOT_MEASURED\",\"postureResult\":null}",
                ExerciseRecordRequest.class);

        assertThat(missing.toValues().postureResult()).isNull();
        assertThat(explicitNull.toValues().postureResult()).isNull();
    }

    @Test
    @DisplayName("필수 값이 없거나 음수면 요청 검증에서 걸린다")
    void beanValidation() throws Exception {
        ExerciseRecordRequest request = mapper.readValue(
                "{\"validCount\":-1}", ExerciseRecordRequest.class);

        assertThat(validator.validate(request))
                .extracting(v -> v.getPropertyPath().toString())
                .containsExactlyInAnyOrder("routineExerciseId", "measurementStatus", "validCount");
    }
}
