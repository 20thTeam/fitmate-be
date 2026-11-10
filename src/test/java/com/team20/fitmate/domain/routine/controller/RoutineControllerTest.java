package com.team20.fitmate.domain.routine.controller;

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
 * 루틴 API의 요청 검증과 응답 형식을 확인하는 테스트.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class RoutineControllerTest {

    @Autowired
    private MockMvc mockMvc;

    private String clientToken;

    @BeforeEach
    void setUp() throws Exception {
        String body = mockMvc.perform(post("/api/participants")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nickname\":\"아현\",\"characterCode\":\"CHARACTER_01\"}"))
                .andReturn().getResponse().getContentAsString();
        clientToken = JsonPath.read(body, "$.data.clientToken");
    }

    @Test
    @DisplayName("목표를 보내면 201-1과 추천 완료 루틴을 응답하고, 같은 루틴을 조회할 수 있다")
    void createAndGet() throws Exception {
        String body = mockMvc.perform(post("/api/routines")
                        .header(ParticipantController.TOKEN_HEADER, clientToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"goal\":\"STRENGTH\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.resultCode").value("201-1"))
                .andExpect(jsonPath("$.data.status").value("COMPLETED"))
                .andExpect(jsonPath("$.data.goalName").value("근력 강화"))
                .andExpect(jsonPath("$.data.exercises[0].sortOrder").value(1))
                .andExpect(jsonPath("$.data.exercises[0].name").isString())
                .andExpect(jsonPath("$.data.exercises[0].exerciseCode").value("SQUAT"))
                .andExpect(jsonPath("$.data.exercises[0].tag").value("근력 강화 · 하체"))
                .andExpect(jsonPath("$.data.exercises[0].routineExerciseId").isNumber())
                .andReturn().getResponse().getContentAsString();
        Integer routineId = JsonPath.read(body, "$.data.routineId");

        mockMvc.perform(get("/api/routines/{routineId}", routineId)
                        .header(ParticipantController.TOKEN_HEADER, clientToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resultCode").value("200-1"))
                .andExpect(jsonPath("$.data.routineId").value(routineId));
    }

    @Test
    @DisplayName("목표 없이 요청하면 400-1과 goal 필드 오류로 응답한다")
    void createWithoutGoal() throws Exception {
        mockMvc.perform(post("/api/routines")
                        .header(ParticipantController.TOKEN_HEADER, clientToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.resultCode").value("400-1"))
                .andExpect(jsonPath("$.data[0].field").value("goal"));
    }

    @Test
    @DisplayName("없는 목표 값이면 400-2로 응답한다")
    void createWithUnknownGoal() throws Exception {
        mockMvc.perform(post("/api/routines")
                        .header(ParticipantController.TOKEN_HEADER, clientToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"goal\":\"FLEXIBILITY\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.resultCode").value("400-2"));
    }

    @Test
    @DisplayName("없는 루틴을 조회하면 404-3으로 응답한다")
    void getUnknownRoutine() throws Exception {
        mockMvc.perform(get("/api/routines/{routineId}", 999999)
                        .header(ParticipantController.TOKEN_HEADER, clientToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.resultCode").value("404-3"));
    }
}
