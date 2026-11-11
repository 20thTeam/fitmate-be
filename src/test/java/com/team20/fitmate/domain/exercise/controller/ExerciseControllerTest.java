package com.team20.fitmate.domain.exercise.controller;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 운동 조회 API가 가이드 시범이 없을 때 시범 없음 상태를 응답하는지 확인하는 테스트.
 * 기본 설정에는 시범 참조가 없다(3D 담당 확정 전).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ExerciseControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("시범 참조가 없는 운동은 200-1과 NONE, null 참조로 응답한다")
    void noGuide() throws Exception {
        mockMvc.perform(get("/api/exercises/{exerciseCode}", "SQUAT"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resultCode").value("200-1"))
                .andExpect(jsonPath("$.data.exerciseCode").value("SQUAT"))
                .andExpect(jsonPath("$.data.name").value("스쿼트"))
                .andExpect(jsonPath("$.data.measureType").value("COUNT"))
                .andExpect(jsonPath("$.data.guideStatus").value("NONE"))
                .andExpect(jsonPath("$.data.guideMotionRef").isEmpty());
    }

    @Test
    @DisplayName("없는 운동 코드면 404-7로 응답한다")
    void unknownExercise() throws Exception {
        mockMvc.perform(get("/api/exercises/{exerciseCode}", "NO_SUCH"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.resultCode").value("404-7"));
    }
}
