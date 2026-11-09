package com.team20.fitmate.domain.room.controller;

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
 * 운동방 API의 응답 형식과 오류 응답을 확인하는 테스트.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class RoomControllerTest {

    @Autowired
    private MockMvc mockMvc;

    private String clientToken;

    private Integer roomId;

    @BeforeEach
    void setUp() throws Exception {
        String participant = mockMvc.perform(post("/api/participants")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nickname\":\"아현\",\"characterCode\":\"CHARACTER_01\"}"))
                .andReturn().getResponse().getContentAsString();
        clientToken = JsonPath.read(participant, "$.data.clientToken");

        String rooms = mockMvc.perform(get("/api/rooms")).andReturn().getResponse().getContentAsString();
        roomId = JsonPath.read(rooms, "$.data[0].roomId");
    }

    @Test
    @DisplayName("운동방 목록은 200-1과 지정 운동방 하나를 응답한다")
    void getAll() throws Exception {
        mockMvc.perform(get("/api/rooms"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resultCode").value("200-1"))
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].capacity").value(5));
    }

    @Test
    @DisplayName("입장하면 200-1과 운동방 정보를 응답하고, 내 정보에 운동방이 연결된다")
    void enterAndCheckMe() throws Exception {
        mockMvc.perform(post("/api/rooms/{roomId}/enter", roomId)
                        .header(ParticipantController.TOKEN_HEADER, clientToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resultCode").value("200-1"))
                .andExpect(jsonPath("$.data.roomId").value(roomId));

        mockMvc.perform(get("/api/participants/me").header(ParticipantController.TOKEN_HEADER, clientToken))
                .andExpect(jsonPath("$.data.roomId").value(roomId));
    }

    @Test
    @DisplayName("없는 운동방은 404-4로 응답한다")
    void unknownRoom() throws Exception {
        mockMvc.perform(get("/api/rooms/{roomId}", 999999))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.resultCode").value("404-4"));

        mockMvc.perform(post("/api/rooms/{roomId}/enter", 999999)
                        .header(ParticipantController.TOKEN_HEADER, clientToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.resultCode").value("404-4"));
    }

    @Test
    @DisplayName("토큰 헤더 없이 입장하면 400-3으로 응답한다")
    void enterWithoutToken() throws Exception {
        mockMvc.perform(post("/api/rooms/{roomId}/enter", roomId))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.resultCode").value("400-3"));
    }
}
