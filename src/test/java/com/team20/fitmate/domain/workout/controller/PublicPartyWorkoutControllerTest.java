package com.team20.fitmate.domain.workout.controller;

import com.jayway.jsonpath.JsonPath;
import com.team20.fitmate.domain.participant.controller.ParticipantController;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 공개 파티 운동 기록 API의 요청 검증과 응답 형식을 확인하는 테스트.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class PublicPartyWorkoutControllerTest {

    @Autowired
    private MockMvc mockMvc;

    private String clientToken;

    @BeforeEach
    void setUp() throws Exception {
        String participant = mockMvc.perform(post("/api/participants")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nickname\":\"아현\",\"characterCode\":\"CHARACTER_01\"}"))
                .andReturn().getResponse().getContentAsString();
        clientToken = JsonPath.read(participant, "$.data.clientToken");
    }

    @Test
    @DisplayName("시작하면 201-1과 공개 파티 기록을, 종료하면 200-1과 점핑잭 기록을, 결과 조회는 팀 없음을 응답한다")
    void startAndFinish() throws Exception {
        String started = mockMvc.perform(post("/api/public-parties/workouts")
                        .header(ParticipantController.TOKEN_HEADER, clientToken))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.resultCode").value("201-1"))
                .andExpect(jsonPath("$.data.kind").value("PUBLIC_PARTY"))
                .andExpect(jsonPath("$.data.routineId").doesNotExist())
                .andExpect(jsonPath("$.data.status").value("IN_PROGRESS"))
                .andReturn().getResponse().getContentAsString();
        Integer workoutId = JsonPath.read(started, "$.data.workoutId");

        mockMvc.perform(post("/api/public-parties/workouts/{workoutId}/finish", workoutId)
                        .header(ParticipantController.TOKEN_HEADER, clientToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"measurementStatus":"MEASURED","validCount":48,"belowStandardCount":3,
                                 "undeterminedCount":1,"actualSeconds":600,
                                 "postureResult":{"feedback":["팔을 머리 위까지 올려 주세요"]}}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resultCode").value("200-1"))
                .andExpect(jsonPath("$.data.status").value("FINISHED"))
                .andExpect(jsonPath("$.data.records[0].exerciseCode").value("JUMPING_JACK"))
                .andExpect(jsonPath("$.data.records[0].validCount").value(48))
                .andExpect(jsonPath("$.data.records[0].postureResult.feedback[0]").value("팔을 머리 위까지 올려 주세요"));

        mockMvc.perform(get("/api/workouts/{workoutId}/result", workoutId)
                        .header(ParticipantController.TOKEN_HEADER, clientToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.workout.kind").value("PUBLIC_PARTY"))
                .andExpect(jsonPath("$.data.teamXp.status").value("NO_TEAM"));
    }

    @Test
    @DisplayName("토큰이 없으면 400-3, 측정 상태가 없거나 횟수가 음수면 400-1로 거부한다")
    void rejectInvalidRequest() throws Exception {
        mockMvc.perform(post("/api/public-parties/workouts"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.resultCode").value("400-3"));

        String started = mockMvc.perform(post("/api/public-parties/workouts")
                        .header(ParticipantController.TOKEN_HEADER, clientToken))
                .andReturn().getResponse().getContentAsString();
        Integer workoutId = JsonPath.read(started, "$.data.workoutId");

        mockMvc.perform(post("/api/public-parties/workouts/{workoutId}/finish", workoutId)
                        .header(ParticipantController.TOKEN_HEADER, clientToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"validCount\":10}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.resultCode").value("400-1"));
        mockMvc.perform(post("/api/public-parties/workouts/{workoutId}/finish", workoutId)
                        .header(ParticipantController.TOKEN_HEADER, clientToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"measurementStatus\":\"MEASURED\",\"validCount\":-1}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.resultCode").value("400-1"));
    }
}
