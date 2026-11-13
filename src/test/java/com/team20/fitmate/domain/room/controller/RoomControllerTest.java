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

import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
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
    @DisplayName("운동방 목록은 200-1과 지정 운동방을 첫 번째로 해서 여러 방을 응답한다")
    void getAll() throws Exception {
        mockMvc.perform(get("/api/rooms"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resultCode").value("200-1"))
                .andExpect(jsonPath("$.data.length()").value(greaterThan(1)))
                .andExpect(jsonPath("$.data[0].name").value("FITMATE 운동방"))
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

    @Test
    @DisplayName("운동방을 만들면 200-1과 새 방·팀 정보를 응답하고, 목록에 나온다")
    void createRoom() throws Exception {
        String created = mockMvc.perform(post("/api/rooms")
                        .header(ParticipantController.TOKEN_HEADER, clientToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"민지의 운동방\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resultCode").value("200-1"))
                .andExpect(jsonPath("$.data.name").value("민지의 운동방"))
                .andExpect(jsonPath("$.data.capacity").value(5))
                .andExpect(jsonPath("$.data.teamId").isNumber())
                .andReturn().getResponse().getContentAsString();
        Integer createdId = JsonPath.read(created, "$.data.roomId");

        mockMvc.perform(get("/api/rooms"))
                .andExpect(jsonPath("$.data[?(@.roomId == " + createdId + ")]", hasSize(1)));
    }

    @Test
    @DisplayName("운동방 이름이 비었거나 20자를 넘으면 400-1로 응답한다")
    void createRoomInvalidName() throws Exception {
        for (String body : new String[]{"{\"name\":\" \"}", "{\"name\":\"" + "가".repeat(21) + "\"}"}) {
            mockMvc.perform(post("/api/rooms")
                            .header(ParticipantController.TOKEN_HEADER, clientToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.resultCode").value("400-1"));
        }
    }

    @Test
    @DisplayName("토큰 헤더 없이 운동방을 만들면 400-3으로 응답한다")
    void createRoomWithoutToken() throws Exception {
        mockMvc.perform(post("/api/rooms")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"방\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.resultCode").value("400-3"));
    }

    @Test
    @DisplayName("운동방에서 나가면 200-1을 응답하고, 내 정보의 roomId가 null이 된다")
    void leaveAndCheckMe() throws Exception {
        mockMvc.perform(post("/api/rooms/{roomId}/enter", roomId)
                .header(ParticipantController.TOKEN_HEADER, clientToken));

        mockMvc.perform(post("/api/rooms/{roomId}/leave", roomId)
                        .header(ParticipantController.TOKEN_HEADER, clientToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resultCode").value("200-1"));

        mockMvc.perform(get("/api/participants/me").header(ParticipantController.TOKEN_HEADER, clientToken))
                .andExpect(jsonPath("$.data.roomId").value(nullValue()));
    }

    @Test
    @DisplayName("없는 운동방에서 나가면 404-4로 응답한다")
    void leaveUnknownRoom() throws Exception {
        mockMvc.perform(post("/api/rooms/{roomId}/leave", 999999)
                        .header(ParticipantController.TOKEN_HEADER, clientToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.resultCode").value("404-4"));
    }
}
