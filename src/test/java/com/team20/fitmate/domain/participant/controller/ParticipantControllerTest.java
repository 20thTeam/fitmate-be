package com.team20.fitmate.domain.participant.controller;

import com.jayway.jsonpath.JsonPath;
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
 * 참가자 API의 요청 검증과 응답 형식을 확인하는 테스트.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ParticipantControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("참가자를 생성하면 201-1과 재방문 토큰을 응답하고, 그 토큰으로 본인 정보를 조회한다")
    void createAndGetMe() throws Exception {
        String body = mockMvc.perform(post("/api/participants")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nickname\":\"아현\",\"characterCode\":\"CHARACTER_01\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.resultCode").value("201-1"))
                .andExpect(jsonPath("$.data.participantId").isNumber())
                .andExpect(jsonPath("$.data.clientToken").isString())
                .andReturn().getResponse().getContentAsString();
        String clientToken = JsonPath.read(body, "$.data.clientToken");

        mockMvc.perform(get("/api/participants/me").header(ParticipantController.TOKEN_HEADER, clientToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resultCode").value("200-1"))
                .andExpect(jsonPath("$.data.nickname").value("아현"))
                .andExpect(jsonPath("$.data.clientToken").doesNotExist());
    }

    @Test
    @DisplayName("닉네임이 비어 있으면 400-1과 nickname 필드 오류로 응답한다")
    void createWithoutNickname() throws Exception {
        mockMvc.perform(post("/api/participants")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nickname\":\" \",\"characterCode\":\"CHARACTER_01\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.resultCode").value("400-1"))
                .andExpect(jsonPath("$.data[0].field").value("nickname"));
    }

    @Test
    @DisplayName("캐릭터를 고르지 않으면 400-1과 characterCode 필드 오류로 응답한다")
    void createWithoutCharacter() throws Exception {
        mockMvc.perform(post("/api/participants")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nickname\":\"아현\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.resultCode").value("400-1"))
                .andExpect(jsonPath("$.data[0].field").value("characterCode"));
    }

    @Test
    @DisplayName("토큰 헤더 없이 조회하면 400-3으로 응답한다")
    void getMeWithoutToken() throws Exception {
        mockMvc.perform(get("/api/participants/me"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.resultCode").value("400-3"));
    }

    @Test
    @DisplayName("없는 토큰으로 조회하면 404-2로 응답한다")
    void getMeWithUnknownToken() throws Exception {
        mockMvc.perform(get("/api/participants/me").header(ParticipantController.TOKEN_HEADER, "없는-토큰"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.resultCode").value("404-2"));
    }
}
