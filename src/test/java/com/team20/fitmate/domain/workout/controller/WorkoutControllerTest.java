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
 * 운동 기록 API의 요청 검증과 응답 형식을 확인하는 테스트.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class WorkoutControllerTest {

    @Autowired
    private MockMvc mockMvc;

    private String clientToken;

    private Integer routineId;

    private Integer squatRoutineExerciseId;

    @BeforeEach
    void setUp() throws Exception {
        String participant = mockMvc.perform(post("/api/participants")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nickname\":\"아현\",\"characterCode\":\"CHARACTER_01\"}"))
                .andReturn().getResponse().getContentAsString();
        clientToken = JsonPath.read(participant, "$.data.clientToken");

        String routine = mockMvc.perform(post("/api/routines")
                        .header(ParticipantController.TOKEN_HEADER, clientToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"goal\":\"POSTURE\"}"))
                .andReturn().getResponse().getContentAsString();
        routineId = JsonPath.read(routine, "$.data.routineId");
        squatRoutineExerciseId = JsonPath.read(routine, "$.data.exercises[0].routineExerciseId");
    }

    /** 운동을 시작하고 운동 기록 식별값을 돌려준다 */
    private Integer startWorkout() throws Exception {
        String body = mockMvc.perform(post("/api/workouts")
                        .header(ParticipantController.TOKEN_HEADER, clientToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"routineId\":" + routineId + "}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.resultCode").value("201-1"))
                .andExpect(jsonPath("$.data.status").value("IN_PROGRESS"))
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.data.workoutId");
    }

    @Test
    @DisplayName("시작 후 종료하면 200-1과 확정된 수행 기록을 응답하고, 결과 조회에서 같은 값과 자세 결과 JSON을 돌려준다")
    void startAndFinish() throws Exception {
        Integer workoutId = startWorkout();
        String finishBody = """
                {"records":[{"routineExerciseId":%d,"measurementStatus":"MEASURED","validCount":10,
                  "belowStandardCount":2,"undeterminedCount":1,"actualSeconds":95,"performedSets":3,
                  "postureResult":{"feedback":["무릎이 발끝을 넘어요"]}}]}
                """.formatted(squatRoutineExerciseId);

        mockMvc.perform(post("/api/workouts/{workoutId}/finish", workoutId)
                        .header(ParticipantController.TOKEN_HEADER, clientToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(finishBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resultCode").value("200-1"))
                .andExpect(jsonPath("$.data.status").value("FINISHED"))
                .andExpect(jsonPath("$.data.records[0].validCount").value(10))
                .andExpect(jsonPath("$.data.records[0].postureResult.feedback[0]").value("무릎이 발끝을 넘어요"))
                .andExpect(jsonPath("$.data.records[1].measurementStatus").value("NOT_MEASURED"))
                .andExpect(jsonPath("$.data.records[1].validCount").doesNotExist())
                .andExpect(jsonPath("$.data.records[1].postureResult").doesNotExist());

        mockMvc.perform(post("/api/workouts/{workoutId}/finish", workoutId)
                        .header(ParticipantController.TOKEN_HEADER, clientToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(finishBody))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.resultCode").value("409-1"));

        mockMvc.perform(get("/api/workouts/{workoutId}/result", workoutId)
                        .header(ParticipantController.TOKEN_HEADER, clientToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resultCode").value("200-1"))
                .andExpect(jsonPath("$.data.workout.status").value("FINISHED"))
                .andExpect(jsonPath("$.data.workout.records[0].validCount").value(10))
                .andExpect(jsonPath("$.data.workout.records[0].postureResult.feedback[0]").value("무릎이 발끝을 넘어요"))
                .andExpect(jsonPath("$.data.teamXp.status").value("NO_TEAM"))
                .andExpect(jsonPath("$.data.teamXp.earnedXp").doesNotExist());
    }

    @Test
    @DisplayName("없는 운동 기록의 결과를 조회하면 404-5로 응답한다")
    void resultOfUnknownWorkout() throws Exception {
        mockMvc.perform(get("/api/workouts/{workoutId}/result", 999999)
                        .header(ParticipantController.TOKEN_HEADER, clientToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.resultCode").value("404-5"));
    }

    @Test
    @DisplayName("루틴 식별값 없이 시작하면 400-1로 응답한다")
    void startWithoutRoutine() throws Exception {
        mockMvc.perform(post("/api/workouts")
                        .header(ParticipantController.TOKEN_HEADER, clientToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.resultCode").value("400-1"));
    }

    @Test
    @DisplayName("수행 기록 목록 없이 종료하거나 음수를 보내면 400-1로 응답한다")
    void finishWithInvalidBody() throws Exception {
        Integer workoutId = startWorkout();

        mockMvc.perform(post("/api/workouts/{workoutId}/finish", workoutId)
                        .header(ParticipantController.TOKEN_HEADER, clientToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.resultCode").value("400-1"));

        mockMvc.perform(post("/api/workouts/{workoutId}/finish", workoutId)
                        .header(ParticipantController.TOKEN_HEADER, clientToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"records\":[{\"routineExerciseId\":%d,\"measurementStatus\":\"MEASURED\",\"validCount\":-1}]}"
                                .formatted(squatRoutineExerciseId)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.resultCode").value("400-1"));
    }

    @Test
    @DisplayName("없는 운동 기록을 종료하면 404-5로 응답한다")
    void finishUnknownWorkout() throws Exception {
        mockMvc.perform(post("/api/workouts/{workoutId}/finish", 999999)
                        .header(ParticipantController.TOKEN_HEADER, clientToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"records\":[]}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.resultCode").value("404-5"));
    }
}
