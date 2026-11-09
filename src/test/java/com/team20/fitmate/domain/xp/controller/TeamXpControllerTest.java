package com.team20.fitmate.domain.xp.controller;

import com.team20.fitmate.domain.room.service.RoomService;
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
 * 팀 경험치 조회 API의 응답 형식을 확인하는 테스트.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class TeamXpControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private RoomService roomService;

    @Test
    @DisplayName("팀 경험치를 조회하면 200-1과 누적 경험치·레벨·다음 레벨 시작 경험치를 응답한다")
    void getTeamXp() throws Exception {
        Long teamId = roomService.getAll().getFirst().teamId();

        mockMvc.perform(get("/api/teams/{teamId}/xp", teamId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resultCode").value("200-1"))
                .andExpect(jsonPath("$.data.teamId").value(teamId))
                .andExpect(jsonPath("$.data.formulaDecided").value(true))
                .andExpect(jsonPath("$.data.totalXp").isNumber())
                .andExpect(jsonPath("$.data.level").isNumber())
                .andExpect(jsonPath("$.data.levelMinXp").isNumber())
                .andExpect(jsonPath("$.data.maxLevel").isBoolean());
    }

    @Test
    @DisplayName("없는 팀이면 404-6으로 응답한다")
    void unknownTeam() throws Exception {
        mockMvc.perform(get("/api/teams/{teamId}/xp", 999999))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.resultCode").value("404-6"));
    }
}
