package com.team20.fitmate.domain.history.controller;

import com.jayway.jsonpath.JsonPath;
import com.team20.fitmate.domain.participant.controller.ParticipantController;
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
 * 성장 기록 API의 응답 형식과 요청 검증을 확인하는 테스트.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class HistoryControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("기록이 없는 참가자가 조회하면 200-1과 빈 회차 목록, null 누적 횟수를 응답한다")
    void emptyHistory() throws Exception {
        String body = mockMvc.perform(post("/api/participants")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nickname\":\"아현\",\"characterCode\":\"CHARACTER_01\"}"))
                .andReturn().getResponse().getContentAsString();
        String token = JsonPath.read(body, "$.data.clientToken");

        mockMvc.perform(get("/api/participants/me/history").header(ParticipantController.TOKEN_HEADER, token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resultCode").value("200-1"))
                .andExpect(jsonPath("$.data.summary.workoutCount").value(0))
                .andExpect(jsonPath("$.data.summary.totalValidCount").isEmpty())
                .andExpect(jsonPath("$.data.workouts").isArray())
                .andExpect(jsonPath("$.data.workouts").isEmpty());
    }

    @Test
    @DisplayName("토큰 헤더가 없으면 400-3, 모르는 토큰이면 404-2로 응답한다")
    void invalidToken() throws Exception {
        mockMvc.perform(get("/api/participants/me/history"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.resultCode").value("400-3"));
        mockMvc.perform(get("/api/participants/me/history").header(ParticipantController.TOKEN_HEADER, "unknown"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.resultCode").value("404-2"));
    }
}
