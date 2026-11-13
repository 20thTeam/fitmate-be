package com.team20.fitmate.domain.contest.controller;

import com.jayway.jsonpath.JsonPath;
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
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 학교 대항 스쿼트 챌린지 API의 요청 검증·응답 형식·이슈 #34 완료 조건을 확인하는 테스트.
 * 시각은 {@link ContestTestClock}으로 고정하고 필요한 만큼 앞으로 돌린다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@Import(ContestTestClock.class)
class UnivContestControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ContestTestClock.MutableClock clock;

    @BeforeEach
    void setUp() {
        clock.set(ContestTestClock.BASE);
    }

    @Test
    @DisplayName("4명이 join하면 같은 판에 들어가고, 5번째는 새 판에 들어간다")
    void fourJoinSameGameFifthGetsNewGame() throws Exception {
        List<String> gameIds = new ArrayList<>();
        for (int i = 1; i <= 4; i++) {
            String body = join("참가자" + i, "seoultech")
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.resultCode").value("200-1"))
                    .andExpect(jsonPath("$.data.playerId").isString())
                    .andExpect(jsonPath("$.data.game.gameId").isString())
                    .andExpect(jsonPath("$.data.game.status").value("WAITING"))
                    .andExpect(jsonPath("$.data.game.players", hasSize(i)))
                    .andReturn().getResponse().getContentAsString();
            gameIds.add(JsonPath.read(body, "$.data.game.gameId"));
        }
        assertThat(gameIds).containsOnly(gameIds.get(0));

        String fifth = join("참가자5", "khu")
                .andExpect(jsonPath("$.data.game.status").value("WAITING"))
                .andExpect(jsonPath("$.data.game.players", hasSize(1)))
                .andExpect(jsonPath("$.data.game.players[0].nickname").value("참가자5"))
                .andReturn().getResponse().getContentAsString();
        assertThat((String) JsonPath.read(fifth, "$.data.game.gameId")).isNotEqualTo(gameIds.get(0));
    }

    @Test
    @DisplayName("판 응답은 이슈 #34의 Game 형식이고 serverNow는 ISO 8601(밀리초)이다")
    void gameResponseShape() throws Exception {
        String body = join("참가자1", "seoultech").andReturn().getResponse().getContentAsString();
        String gameId = JsonPath.read(body, "$.data.game.gameId");
        String playerId = JsonPath.read(body, "$.data.playerId");

        mockMvc.perform(get("/api/univ-contest/games/{gameId}", gameId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resultCode").value("200-1"))
                .andExpect(jsonPath("$.data.gameId").value(gameId))
                .andExpect(jsonPath("$.data.status").value("WAITING"))
                .andExpect(jsonPath("$.data.startAt").value(nullValue()))
                .andExpect(jsonPath("$.data.durationSeconds").value(20))
                .andExpect(jsonPath("$.data.maxPlayers").value(4))
                .andExpect(jsonPath("$.data.serverNow").value("2026-11-13T05:00:00.000Z"))
                .andExpect(jsonPath("$.data.players[0].playerId").value(playerId))
                .andExpect(jsonPath("$.data.players[0].nickname").value("참가자1"))
                .andExpect(jsonPath("$.data.players[0].schoolId").value("seoultech"))
                .andExpect(jsonPath("$.data.players[0].reps").value(nullValue()))
                .andExpect(jsonPath("$.data.players[0].submitted").value(false));
    }

    @Test
    @DisplayName("start는 startAt을 지금+5초로 정하고, 다른 사람이 나중에 눌러도 startAt이 바뀌지 않는다")
    void startSetsStartAtOnce() throws Exception {
        String first = join("참가자1", "seoultech").andReturn().getResponse().getContentAsString();
        String gameId = JsonPath.read(first, "$.data.game.gameId");
        String player1 = JsonPath.read(first, "$.data.playerId");
        String second = join("참가자2", "khu").andReturn().getResponse().getContentAsString();
        String player2 = JsonPath.read(second, "$.data.playerId");

        start(gameId, player1)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("COUNTDOWN"))
                .andExpect(jsonPath("$.data.startAt").value("2026-11-13T05:00:05.000Z"))
                .andExpect(jsonPath("$.data.serverNow").value("2026-11-13T05:00:00.000Z"));

        clock.advance(Duration.ofMillis(1500));
        start(gameId, player2)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.startAt").value("2026-11-13T05:00:05.000Z"))
                .andExpect(jsonPath("$.data.serverNow").value("2026-11-13T05:00:01.500Z"));

        // 시작된 판에는 새로 들어가지 않고 새 판이 생긴다
        String third = join("참가자3", "kw").andReturn().getResponse().getContentAsString();
        assertThat((String) JsonPath.read(third, "$.data.game.gameId")).isNotEqualTo(gameId);

        clock.advance(Duration.ofMillis(3500));
        mockMvc.perform(get("/api/univ-contest/games/{gameId}", gameId))
                .andExpect(jsonPath("$.data.status").value("RUNNING"));
    }

    @Test
    @DisplayName("같은 사람이 result를 두 번 내도 처음 값이 남고 두 번째도 성공(200)이다")
    void resultKeepsFirstValue() throws Exception {
        String first = join("참가자1", "seoultech").andReturn().getResponse().getContentAsString();
        String gameId = JsonPath.read(first, "$.data.game.gameId");
        String player1 = JsonPath.read(first, "$.data.playerId");
        join("참가자2", "khu");
        start(gameId, player1);
        clock.advance(Duration.ofSeconds(26));

        result(gameId, player1, "18")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("RUNNING"))
                .andExpect(jsonPath("$.data.players[0].reps").value(18))
                .andExpect(jsonPath("$.data.players[0].submitted").value(true))
                .andExpect(jsonPath("$.data.players[1].submitted").value(false));

        result(gameId, player1, "25")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resultCode").value("200-1"))
                .andExpect(jsonPath("$.data.players[0].reps").value(18));
    }

    @Test
    @DisplayName("모두 제출하면 FINISHED가 되고, reps가 null인 기록은 standings에서 빠진다")
    void allSubmittedFinishesAndNullExcludedFromStandings() throws Exception {
        String first = join("민준", "seoultech").andReturn().getResponse().getContentAsString();
        String gameId = JsonPath.read(first, "$.data.game.gameId");
        String p1 = JsonPath.read(first, "$.data.playerId");
        String p2 = JsonPath.read(join("서연", "seoultech").andReturn().getResponse().getContentAsString(), "$.data.playerId");
        String p3 = JsonPath.read(join("지호", "khu").andReturn().getResponse().getContentAsString(), "$.data.playerId");
        String p4 = JsonPath.read(join("하은", "kw").andReturn().getResponse().getContentAsString(), "$.data.playerId");
        start(gameId, p1);
        clock.advance(Duration.ofSeconds(26));

        result(gameId, p1, "21");
        result(gameId, p2, "0");
        result(gameId, p3, "15");
        // 끝나기 전에는 현황에 들어가지 않는다
        standings().andExpect(jsonPath("$.data.gameCount").value(0))
                .andExpect(jsonPath("$.data.schools", hasSize(0)));

        result(gameId, p4, "null")
                .andExpect(jsonPath("$.data.status").value("FINISHED"))
                .andExpect(jsonPath("$.data.players[3].reps").value(nullValue()))
                .andExpect(jsonPath("$.data.players[3].submitted").value(true));

        standings()
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resultCode").value("200-1"))
                .andExpect(jsonPath("$.data.gameCount").value(1))
                // kw는 null 기록뿐이라 빠진다. 0회는 측정된 기록이라 들어간다
                .andExpect(jsonPath("$.data.schools", hasSize(2)))
                .andExpect(jsonPath("$.data.schools[0].schoolId").value("seoultech"))
                .andExpect(jsonPath("$.data.schools[0].totalReps").value(21))
                .andExpect(jsonPath("$.data.schools[0].entryCount").value(2))
                .andExpect(jsonPath("$.data.schools[1].schoolId").value("khu"))
                .andExpect(jsonPath("$.data.schools[1].totalReps").value(15))
                .andExpect(jsonPath("$.data.topPlayers", hasSize(3)))
                .andExpect(jsonPath("$.data.topPlayers[0].nickname").value("민준"))
                .andExpect(jsonPath("$.data.topPlayers[0].reps").value(21))
                .andExpect(jsonPath("$.data.topPlayers[1].nickname").value("지호"))
                .andExpect(jsonPath("$.data.topPlayers[2].reps").value(0));
    }

    @Test
    @DisplayName("startAt+30초가 지나면 FINISHED가 되고, 안 낸 사람은 reps null·submitted false로 남으며 늦은 제출은 받지 않는다")
    void deadlineFinishesGame() throws Exception {
        String first = join("민준", "seoultech").andReturn().getResponse().getContentAsString();
        String gameId = JsonPath.read(first, "$.data.game.gameId");
        String p1 = JsonPath.read(first, "$.data.playerId");
        String p2 = JsonPath.read(join("지호", "khu").andReturn().getResponse().getContentAsString(), "$.data.playerId");
        start(gameId, p1);
        clock.advance(Duration.ofSeconds(25));
        result(gameId, p1, "10");

        // startAt(+5초) + 30초 = 기준 + 35초 직전까지는 RUNNING
        clock.set(ContestTestClock.BASE.plusSeconds(35).minusMillis(1));
        mockMvc.perform(get("/api/univ-contest/games/{gameId}", gameId))
                .andExpect(jsonPath("$.data.status").value("RUNNING"));

        clock.set(ContestTestClock.BASE.plusSeconds(35));
        mockMvc.perform(get("/api/univ-contest/games/{gameId}", gameId))
                .andExpect(jsonPath("$.data.status").value("FINISHED"))
                .andExpect(jsonPath("$.data.players[1].reps").value(nullValue()))
                .andExpect(jsonPath("$.data.players[1].submitted").value(false));

        result(gameId, p2, "30")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("FINISHED"))
                .andExpect(jsonPath("$.data.players[1].reps").value(nullValue()))
                .andExpect(jsonPath("$.data.players[1].submitted").value(false));

        standings()
                .andExpect(jsonPath("$.data.gameCount").value(1))
                .andExpect(jsonPath("$.data.schools", hasSize(1)))
                .andExpect(jsonPath("$.data.topPlayers", hasSize(1)));
    }

    @Test
    @DisplayName("topPlayers는 reps 큰 순 상위 5개이고, 측정 기록이 없는 끝난 판은 gameCount에 넣지 않는다")
    void standingsTopFiveAndGameCount() throws Exception {
        int[][] gamesReps = {{3, 9, 4, 12}, {7, 30}};
        for (int[] reps : gamesReps) {
            playFinishedGame(reps);
        }
        // 측정 안 된 기록만 있는 판
        String body = join("미측정", "hufs").andReturn().getResponse().getContentAsString();
        String gameId = JsonPath.read(body, "$.data.game.gameId");
        String playerId = JsonPath.read(body, "$.data.playerId");
        start(gameId, playerId);
        clock.advance(Duration.ofSeconds(26));
        result(gameId, playerId, "null").andExpect(jsonPath("$.data.status").value("FINISHED"));

        standings()
                .andExpect(jsonPath("$.data.gameCount").value(2))
                .andExpect(jsonPath("$.data.topPlayers", hasSize(5)))
                .andExpect(jsonPath("$.data.topPlayers[0].reps").value(30))
                .andExpect(jsonPath("$.data.topPlayers[1].reps").value(12))
                .andExpect(jsonPath("$.data.topPlayers[2].reps").value(9))
                .andExpect(jsonPath("$.data.topPlayers[3].reps").value(7))
                .andExpect(jsonPath("$.data.topPlayers[4].reps").value(4))
                .andExpect(jsonPath("$.data.schools[0].schoolId").value("ptu"))
                .andExpect(jsonPath("$.data.schools[0].totalReps").value(65))
                .andExpect(jsonPath("$.data.schools[0].entryCount").value(6));
    }

    @Test
    @DisplayName("시작 전 leave는 판에서 빼고, 시작 후 leave는 무시한다")
    void leave() throws Exception {
        String first = join("참가자1", "seoultech").andReturn().getResponse().getContentAsString();
        String gameId = JsonPath.read(first, "$.data.game.gameId");
        String p1 = JsonPath.read(first, "$.data.playerId");
        String p2 = JsonPath.read(join("참가자2", "khu").andReturn().getResponse().getContentAsString(), "$.data.playerId");

        playerAction(gameId, "leave", p2)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.players", hasSize(1)))
                .andExpect(jsonPath("$.data.serverNow").isString());
        // 이미 나간 사람이 다시 보내도 성공
        playerAction(gameId, "leave", p2).andExpect(status().isOk());

        start(gameId, p1);
        playerAction(gameId, "leave", p1)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.players", hasSize(1)))
                .andExpect(jsonPath("$.data.status").value("COUNTDOWN"));
    }

    @Test
    @DisplayName("playerId는 문자열 \"41\"과 숫자 41 모두 받는다")
    void playerIdAsStringOrNumber() throws Exception {
        String first = join("참가자1", "seoultech").andReturn().getResponse().getContentAsString();
        String gameId = JsonPath.read(first, "$.data.game.gameId");
        String p1 = JsonPath.read(first, "$.data.playerId");

        mockMvc.perform(post("/api/univ-contest/games/{gameId}/start", gameId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"playerId\":" + p1 + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("COUNTDOWN"));
    }

    @Test
    @DisplayName("오류: 없는 판 404-9, 판에 없는 playerId 404-10, 시작 전 결과 409-5, 값 검증 400-1, gameId 형식 400-3")
    void errors() throws Exception {
        mockMvc.perform(get("/api/univ-contest/games/{gameId}", 999999))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.resultCode").value("404-9"));
        playerAction("999999", "start", "1")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.resultCode").value("404-9"));
        playerAction("999999", "leave", "1")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.resultCode").value("404-9"));

        String first = join("참가자1", "seoultech").andReturn().getResponse().getContentAsString();
        String gameId = JsonPath.read(first, "$.data.game.gameId");
        String p1 = JsonPath.read(first, "$.data.playerId");

        start(gameId, "999999")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.resultCode").value("404-10"));
        result(gameId, p1, "10")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.resultCode").value("409-5"));

        start(gameId, p1);
        result(gameId, p1, "-1")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.resultCode").value("400-1"));
        join(" ", "seoultech")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.resultCode").value("400-1"));
        join("참가자", "")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.resultCode").value("400-1"));
        mockMvc.perform(get("/api/univ-contest/games/{gameId}", "abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.resultCode").value("400-3"));
    }

    @Test
    @DisplayName("Swagger 문서에 챌린지 API 여섯 개가 나온다")
    void swaggerDocs() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/univ-contest/standings'].get.summary").value("현황 조회"))
                .andExpect(jsonPath("$.paths['/api/univ-contest/games/join'].post.summary").value("판 참가"))
                .andExpect(jsonPath("$.paths['/api/univ-contest/games/{gameId}'].get.summary").value("판 상태 조회"))
                .andExpect(jsonPath("$.paths['/api/univ-contest/games/{gameId}/start'].post.summary").value("판 시작"))
                .andExpect(jsonPath("$.paths['/api/univ-contest/games/{gameId}/result'].post.summary").value("결과 제출"))
                .andExpect(jsonPath("$.paths['/api/univ-contest/games/{gameId}/leave'].post.summary").value("판 나가기"));
    }

    /**
     * 한 학교(ptu) 사람들로 판 하나를 채워 시작하고 모두 결과를 내 끝낸다.
     */
    private void playFinishedGame(int[] reps) throws Exception {
        String gameId = null;
        List<String> playerIds = new ArrayList<>();
        for (int i = 0; i < reps.length; i++) {
            String body = join("ptu" + reps[i], "ptu").andReturn().getResponse().getContentAsString();
            gameId = JsonPath.read(body, "$.data.game.gameId");
            playerIds.add(JsonPath.read(body, "$.data.playerId"));
        }
        start(gameId, playerIds.get(0));
        clock.advance(Duration.ofSeconds(26));
        for (int i = 0; i < reps.length; i++) {
            result(gameId, playerIds.get(i), String.valueOf(reps[i]));
        }
        mockMvc.perform(get("/api/univ-contest/games/{gameId}", gameId))
                .andExpect(jsonPath("$.data.status").value("FINISHED"));
    }

    private ResultActions join(String nickname, String schoolId) throws Exception {
        return mockMvc.perform(post("/api/univ-contest/games/join")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"nickname\":\"" + nickname + "\",\"schoolId\":\"" + schoolId + "\"}"));
    }

    private ResultActions start(String gameId, String playerId) throws Exception {
        return playerAction(gameId, "start", playerId);
    }

    private ResultActions playerAction(String gameId, String action, String playerId) throws Exception {
        return mockMvc.perform(post("/api/univ-contest/games/{gameId}/" + action, gameId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"playerId\":\"" + playerId + "\"}"));
    }

    private ResultActions result(String gameId, String playerId, String reps) throws Exception {
        return mockMvc.perform(post("/api/univ-contest/games/{gameId}/result", gameId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"playerId\":\"" + playerId + "\",\"reps\":" + reps + "}"));
    }

    private ResultActions standings() throws Exception {
        return mockMvc.perform(get("/api/univ-contest/standings"));
    }
}
