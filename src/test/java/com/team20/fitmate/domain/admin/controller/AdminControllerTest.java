package com.team20.fitmate.domain.admin.controller;

import com.jayway.jsonpath.JsonPath;
import com.team20.fitmate.domain.admin.auth.AdminKeyInterceptor;
import com.team20.fitmate.domain.participant.controller.ParticipantController;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 운영 API의 운영 키 인증, 응답 형식(토큰 미노출·미측정 null), 초기화 범위를 확인하는 테스트.
 */
@SpringBootTest(properties = "admin.key=" + AdminControllerTest.ADMIN_KEY)
@AutoConfigureMockMvc
@Transactional
class AdminControllerTest {

    static final String ADMIN_KEY = "test-admin-key";

    @Autowired
    private MockMvc mockMvc;

    private Integer roomId;

    private Integer teamId;

    @BeforeEach
    void setUp() throws Exception {
        String rooms = mockMvc.perform(get("/api/rooms")).andReturn().getResponse().getContentAsString();
        roomId = JsonPath.read(rooms, "$.data[0].roomId");
        teamId = JsonPath.read(rooms, "$.data[0].teamId");
    }

    /** 운영 키를 붙인 요청 */
    private static MockHttpServletRequestBuilder admin(MockHttpServletRequestBuilder request) {
        return request.header(AdminKeyInterceptor.ADMIN_KEY_HEADER, ADMIN_KEY);
    }

    /** 참가자를 만들고 재방문 토큰을 돌려준다 */
    private String createParticipant(String nickname) throws Exception {
        String body = mockMvc.perform(post("/api/participants")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nickname\":\"" + nickname + "\",\"characterCode\":\"CHARACTER_01\"}"))
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.data.clientToken");
    }

    /** 토큰의 참가자 식별값 */
    private Integer participantIdOf(String token) throws Exception {
        String body = mockMvc.perform(get("/api/participants/me").header(ParticipantController.TOKEN_HEADER, token))
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.data.participantId");
    }

    /** 지정 운동방에 REST 입장한다 */
    private void enterRoom(String token) throws Exception {
        mockMvc.perform(post("/api/rooms/{roomId}/enter", roomId).header(ParticipantController.TOKEN_HEADER, token))
                .andExpect(status().isOk());
    }

    /** 루틴을 만들고 운동을 시작해 [운동 기록 식별값, 첫 루틴 운동 식별값]을 돌려준다 */
    private int[] startWorkout(String token) throws Exception {
        String routine = mockMvc.perform(post("/api/routines")
                        .header(ParticipantController.TOKEN_HEADER, token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"goal\":\"POSTURE\"}"))
                .andReturn().getResponse().getContentAsString();
        Integer routineId = JsonPath.read(routine, "$.data.routineId");
        Integer routineExerciseId = JsonPath.read(routine, "$.data.exercises[0].routineExerciseId");

        String workout = mockMvc.perform(post("/api/workouts")
                        .header(ParticipantController.TOKEN_HEADER, token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"routineId\":" + routineId + "}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return new int[]{JsonPath.read(workout, "$.data.workoutId"), routineExerciseId};
    }

    /** 첫 운동 하나의 수행 기록으로 운동을 종료한다 */
    private void finishWorkout(String token, int[] workout, String record) throws Exception {
        mockMvc.perform(post("/api/workouts/{workoutId}/finish", workout[0])
                        .header(ParticipantController.TOKEN_HEADER, token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"records\":[{\"routineExerciseId\":" + workout[1] + "," + record + "}]}"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("운영 키가 없거나 틀리면 네 API 모두 401-1로 막는다")
    void rejectsMissingOrWrongKey() throws Exception {
        List<MockHttpServletRequestBuilder> requests = List.of(
                get("/api/admin/rooms"), get("/api/admin/participants"),
                get("/api/admin/workouts"), post("/api/admin/reset"));
        for (MockHttpServletRequestBuilder request : requests) {
            mockMvc.perform(request)
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.resultCode").value("401-1"));
        }

        List<MockHttpServletRequestBuilder> wrongKey = List.of(
                get("/api/admin/rooms"), get("/api/admin/participants"),
                get("/api/admin/workouts"), post("/api/admin/reset"));
        for (MockHttpServletRequestBuilder request : wrongKey) {
            mockMvc.perform(request.header(AdminKeyInterceptor.ADMIN_KEY_HEADER, ADMIN_KEY + "x"))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.resultCode").value("401-1"));
        }
    }

    @Test
    @DisplayName("틀린 키로 초기화를 부르면 아무것도 지우지 않는다")
    void wrongKeyResetDeletesNothing() throws Exception {
        String token = createParticipant("민지");

        mockMvc.perform(post("/api/admin/reset").header(AdminKeyInterceptor.ADMIN_KEY_HEADER, "wrong"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/participants/me").header(ParticipantController.TOKEN_HEADER, token))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("브라우저 CORS 사전 요청(OPTIONS)은 운영 키 없이 통과하고 X-Admin-Key 헤더를 허용한다")
    void preflightPassesWithoutKey() throws Exception {
        mockMvc.perform(options("/api/admin/rooms")
                        .header(HttpHeaders.ORIGIN, "http://localhost:5173")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, AdminKeyInterceptor.ADMIN_KEY_HEADER))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "http://localhost:5173"));
    }

    @Test
    @DisplayName("방 접속자 현황은 REST 입장한 참가자를 방별로 담고, 웹소켓 연결이 없으면 connected=false다")
    void rooms() throws Exception {
        String token = createParticipant("민지");
        enterRoom(token);
        createParticipant("입장전");
        Integer participantId = participantIdOf(token);

        mockMvc.perform(admin(get("/api/admin/rooms")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resultCode").value("200-1"))
                .andExpect(jsonPath("$.data[0].roomId").value(roomId))
                .andExpect(jsonPath("$.data[0].teamId").value(teamId))
                .andExpect(jsonPath("$.data[0].capacity").isNumber())
                .andExpect(jsonPath("$.data[0].participants[?(@.participantId == %d)].nickname", participantId)
                        .value(contains("민지")))
                .andExpect(jsonPath("$.data[0].participants[?(@.participantId == %d)].characterCode", participantId)
                        .value(contains("CHARACTER_01")))
                .andExpect(jsonPath("$.data[0].participants[?(@.participantId == %d)].connected", participantId)
                        .value(contains(false)))
                .andExpect(jsonPath("$.data[*].participants[?(@.nickname == '입장전')]").value(empty()));
    }

    @Test
    @DisplayName("전체 참가자 목록은 최근 생성 순이고 생성 시각·확정 운동 수를 담으며 재방문 토큰은 없다")
    void participants() throws Exception {
        String first = createParticipant("먼저");
        enterRoom(first);
        finishWorkout(first, startWorkout(first), "\"measurementStatus\":\"MEASURED\",\"validCount\":10");
        startWorkout(first); // 진행 중 기록은 세지 않는다
        String second = createParticipant("나중");
        Integer firstId = participantIdOf(first);
        Integer secondId = participantIdOf(second);

        String body = mockMvc.perform(admin(get("/api/admin/participants")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resultCode").value("200-1"))
                .andExpect(jsonPath("$.data[0].participantId").value(secondId))
                .andExpect(jsonPath("$.data[1].participantId").value(firstId))
                .andExpect(jsonPath("$.data[0].roomId").value(nullValue()))
                .andExpect(jsonPath("$.data[0].workoutCount").value(0))
                .andExpect(jsonPath("$.data[1].roomId").value(roomId))
                .andExpect(jsonPath("$.data[1].workoutCount").value(1))
                .andExpect(jsonPath("$.data[1].createdAt").isString())
                .andExpect(jsonPath("$.data[*].clientToken").value(empty()))
                .andReturn().getResponse().getContentAsString();

        assertThat(body).doesNotContain(first).doesNotContain(second).doesNotContain("clientToken");
    }

    @Test
    @DisplayName("운동 기록 목록은 진행 중을 포함해 최근 시작 순이고, 확정 전·미측정 값은 0이 아니라 null이다")
    void workouts() throws Exception {
        String token = createParticipant("민지");
        enterRoom(token);
        int[] measured = startWorkout(token);
        finishWorkout(token, measured, "\"measurementStatus\":\"MEASURED\",\"validCount\":10");
        int[] notMeasured = startWorkout(token);
        finishWorkout(token, notMeasured, "\"measurementStatus\":\"NOT_MEASURED\"");
        int[] inProgress = startWorkout(token);
        Integer participantId = participantIdOf(token);

        mockMvc.perform(admin(get("/api/admin/workouts").param("participantId", participantId.toString())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resultCode").value("200-1"))
                .andExpect(jsonPath("$.data", hasSize(3)))
                .andExpect(jsonPath("$.data[*].participantId", everyItem(is(participantId))))
                .andExpect(jsonPath("$.data[*].nickname", everyItem(is("민지"))))
                .andExpect(jsonPath("$.data[*].roomId", everyItem(is(roomId))))
                // 최근 시작 순
                .andExpect(jsonPath("$.data[0].workoutId").value(inProgress[0]))
                .andExpect(jsonPath("$.data[1].workoutId").value(notMeasured[0]))
                .andExpect(jsonPath("$.data[2].workoutId").value(measured[0]))
                // 진행 중: 확정값은 모두 null
                .andExpect(jsonPath("$.data[0].status").value("IN_PROGRESS"))
                .andExpect(jsonPath("$.data[0].startedAt").isString())
                .andExpect(jsonPath("$.data[0].finishedAt").value(nullValue()))
                .andExpect(jsonPath("$.data[0].elapsedSeconds").value(nullValue()))
                .andExpect(jsonPath("$.data[0].totalValidCount").value(nullValue()))
                .andExpect(jsonPath("$.data[0].earnedXp").value(nullValue()))
                // 미측정 종료: 유효 횟수 null(0 아님)
                .andExpect(jsonPath("$.data[1].status").value("FINISHED"))
                .andExpect(jsonPath("$.data[1].totalValidCount").value(nullValue()))
                .andExpect(jsonPath("$.data[1].elapsedSeconds").isNumber())
                // 측정 종료: 유효 횟수·경험치·루틴 정보
                .andExpect(jsonPath("$.data[2].status").value("FINISHED"))
                .andExpect(jsonPath("$.data[2].totalValidCount").value(10))
                .andExpect(jsonPath("$.data[2].earnedXp").isNumber())
                .andExpect(jsonPath("$.data[2].finishedAt").isString())
                .andExpect(jsonPath("$.data[2].kind").value("ROUTINE"))
                .andExpect(jsonPath("$.data[2].roundNo").value(1))
                .andExpect(jsonPath("$.data[2].goal").value("POSTURE"))
                .andExpect(jsonPath("$.data[2].goalName").value("자세 개선"))
                .andExpect(jsonPath("$.data[2].routineId").isNumber())
                .andExpect(jsonPath("$.data[2].records").doesNotExist());
    }

    @Test
    @DisplayName("participantId 없이 부르면 모든 참가자 기록을, 없는 참가자면 빈 목록을, 형식이 틀리면 400-3을 준다")
    void workoutsFilter() throws Exception {
        String a = createParticipant("가");
        String b = createParticipant("나");
        startWorkout(a);
        startWorkout(b);

        mockMvc.perform(admin(get("/api/admin/workouts")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(greaterThanOrEqualTo(2))))
                .andExpect(jsonPath("$.data[?(@.nickname == '가')]", hasSize(1)))
                .andExpect(jsonPath("$.data[?(@.nickname == '나')]", hasSize(1)))
                // 운동방 입장 없이 시작한 기록은 운동방이 없다
                .andExpect(jsonPath("$.data[?(@.nickname == '가')].roomId", contains(nullValue())));

        mockMvc.perform(admin(get("/api/admin/workouts").param("participantId", "999999")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", empty()));

        mockMvc.perform(admin(get("/api/admin/workouts").param("participantId", "abc")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.resultCode").value("400-3"));
    }

    @Test
    @DisplayName("초기화하면 참가자·기록·팀 경험치를 지우고 운동방·팀은 남기며, 기존 토큰은 404-2가 된다")
    void reset() throws Exception {
        String token = createParticipant("민지");
        enterRoom(token);
        finishWorkout(token, startWorkout(token), "\"measurementStatus\":\"MEASURED\",\"validCount\":10");
        // 2회차 추천: 루틴이 종료된 운동 기록을 근거로 가리키는 상호 참조를 만든다
        String history = mockMvc.perform(admin(get("/api/admin/workouts").param("participantId",
                        participantIdOf(token).toString())))
                .andReturn().getResponse().getContentAsString();
        Integer finishedWorkoutId = JsonPath.read(history, "$.data[0].workoutId");
        mockMvc.perform(post("/api/routines/next-round")
                        .header(ParticipantController.TOKEN_HEADER, token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"workoutId\":" + finishedWorkoutId + "}"))
                .andExpect(status().is2xxSuccessful());
        startWorkout(token); // 진행 중 기록도 지운다
        mockMvc.perform(get("/api/teams/{teamId}/xp", teamId))
                .andExpect(jsonPath("$.data.totalXp", not(is(0))));

        mockMvc.perform(admin(post("/api/admin/reset")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resultCode").value("200-1"))
                .andExpect(jsonPath("$.data.deletedParticipants", greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$.data.deletedWorkouts", greaterThanOrEqualTo(2)))
                .andExpect(jsonPath("$.data.resetTeams", greaterThanOrEqualTo(1)));

        mockMvc.perform(get("/api/participants/me").header(ParticipantController.TOKEN_HEADER, token))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.resultCode").value("404-2"));
        mockMvc.perform(admin(get("/api/admin/participants")))
                .andExpect(jsonPath("$.data", empty()));
        mockMvc.perform(admin(get("/api/admin/workouts")))
                .andExpect(jsonPath("$.data", empty()));
        mockMvc.perform(get("/api/rooms"))
                .andExpect(jsonPath("$.data[0].roomId").value(roomId));
        mockMvc.perform(get("/api/teams/{teamId}/xp", teamId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalXp").value(0));
    }
}
