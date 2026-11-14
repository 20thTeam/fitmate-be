package com.team20.fitmate.domain.admin.controller;

import com.jayway.jsonpath.JsonPath;
import com.team20.fitmate.domain.admin.auth.AdminKeyInterceptor;
import com.team20.fitmate.domain.contest.ContestTestClock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;

import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 챌린지 기록 초기화 운영 API(이슈 #41)의 운영 키 확인과 삭제 범위를 확인하는 테스트.
 */
@SpringBootTest(properties = "admin.key=" + AdminContestControllerTest.ADMIN_KEY)
@AutoConfigureMockMvc
@Transactional
@Import(ContestTestClock.class)
class AdminContestControllerTest {

    static final String ADMIN_KEY = "test-admin-key";

    private static final String RESET_URL = "/api/admin/univ-contest/reset";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ContestTestClock.MutableClock clock;

    /** 끝난 판 */
    private String finishedGameId;

    /** 진행 중인(COUNTDOWN) 판 */
    private String runningGameId;

    /** 모집 중인 판 */
    private String waitingGameId;

    /**
     * 끝난 판 하나, 시작한 판 하나, 모집 중인 판 하나를 만든다.
     */
    @BeforeEach
    void setUp() throws Exception {
        clock.set(ContestTestClock.BASE);

        String a = join("민준", "seoultech");
        String b = join("지호", "khu");
        finishedGameId = JsonPath.read(a, "$.data.game.gameId");
        playerAction(finishedGameId, "start", JsonPath.read(a, "$.data.playerId"));
        clock.advance(Duration.ofSeconds(26));
        result(finishedGameId, JsonPath.read(a, "$.data.playerId"), 21);
        result(finishedGameId, JsonPath.read(b, "$.data.playerId"), 15);

        // 첫 판은 시작됐으므로 아래 두 사람은 새 판에 들어간다
        String c = join("서연", "kw");
        runningGameId = JsonPath.read(c, "$.data.game.gameId");
        join("하은", "kw");
        playerAction(runningGameId, "start", JsonPath.read(c, "$.data.playerId"));

        String d = join("도윤", "hufs");
        waitingGameId = JsonPath.read(d, "$.data.game.gameId");

        mockMvc.perform(get("/api/univ-contest/standings"))
                .andExpect(jsonPath("$.data.gameCount").value(1))
                .andExpect(jsonPath("$.data.schools", hasSize(2)));
    }

    @Test
    @DisplayName("운영 키가 맞으면 끝난 판·진행 중인 판·모집 중인 판과 참가자가 모두 지워지고 현황이 비어 있다")
    void resetDeletesEverything() throws Exception {
        mockMvc.perform(post(RESET_URL).header(AdminKeyInterceptor.ADMIN_KEY_HEADER, ADMIN_KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resultCode").value("200-1"))
                .andExpect(jsonPath("$.data.deletedGames").value(3))
                .andExpect(jsonPath("$.data.deletedPlayers").value(5));

        mockMvc.perform(get("/api/univ-contest/standings"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.gameCount").value(0))
                .andExpect(jsonPath("$.data.schools", empty()))
                .andExpect(jsonPath("$.data.topPlayers", empty()));
        for (String gameId : new String[]{finishedGameId, runningGameId, waitingGameId}) {
            mockMvc.perform(get("/api/univ-contest/games/{gameId}", gameId))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.resultCode").value("404-9"));
        }

        // 지운 뒤에도 새 판에 정상으로 들어간다
        String fresh = join("새참가자", "ptu");
        mockMvc.perform(get("/api/univ-contest/games/{gameId}", (String) JsonPath.read(fresh, "$.data.game.gameId")))
                .andExpect(jsonPath("$.data.players", hasSize(1)));
    }

    @Test
    @DisplayName("운영 키가 없거나 틀리면 401-1이고 아무것도 지우지 않는다")
    void wrongKeyDeletesNothing() throws Exception {
        mockMvc.perform(post(RESET_URL))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.resultCode").value("401-1"));
        mockMvc.perform(post(RESET_URL).header(AdminKeyInterceptor.ADMIN_KEY_HEADER, "wrong-key"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.resultCode").value("401-1"));

        mockMvc.perform(get("/api/univ-contest/standings"))
                .andExpect(jsonPath("$.data.gameCount").value(1))
                .andExpect(jsonPath("$.data.topPlayers", hasSize(2)));
        mockMvc.perform(get("/api/univ-contest/games/{gameId}", runningGameId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.players", hasSize(2)));
        mockMvc.perform(get("/api/univ-contest/games/{gameId}", waitingGameId))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Swagger 문서의 운영 태그에 챌린지 기록 초기화가 나온다")
    void swaggerDocs() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/admin/univ-contest/reset'].post.summary").value("챌린지 기록 초기화"))
                .andExpect(jsonPath("$.paths['/api/admin/univ-contest/reset'].post.tags[0]").value("운영"));
    }

    private String join(String nickname, String schoolId) throws Exception {
        return mockMvc.perform(post("/api/univ-contest/games/join")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nickname\":\"" + nickname + "\",\"schoolId\":\"" + schoolId + "\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
    }

    private ResultActions playerAction(String gameId, String action, String playerId) throws Exception {
        return mockMvc.perform(post("/api/univ-contest/games/{gameId}/" + action, gameId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"playerId\":\"" + playerId + "\"}"))
                .andExpect(status().isOk());
    }

    private void result(String gameId, String playerId, int reps) throws Exception {
        mockMvc.perform(post("/api/univ-contest/games/{gameId}/result", gameId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"playerId\":\"" + playerId + "\",\"reps\":" + reps + "}"))
                .andExpect(status().isOk());
    }
}
