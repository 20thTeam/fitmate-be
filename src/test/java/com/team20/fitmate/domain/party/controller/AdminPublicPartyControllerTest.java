package com.team20.fitmate.domain.party.controller;

import com.jayway.jsonpath.JsonPath;
import com.team20.fitmate.domain.admin.auth.AdminKeyInterceptor;
import com.team20.fitmate.domain.participant.controller.ParticipantController;
import com.team20.fitmate.domain.party.entity.PartyMode;
import com.team20.fitmate.domain.party.service.PublicPartyService;
import com.team20.fitmate.domain.workout.PublicPartyTestClock;
import com.team20.fitmate.global.exception.CustomException;
import com.team20.fitmate.global.exception.ErrorCode;
import org.junit.jupiter.api.AfterEach;
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

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 운영 화면의 공개 파티 운영 모드·운영 시각 API와, 그 값이 홈 카드 조회·운동 기록·실시간 입장 판단에 같이 반영되는지 확인한다 (이슈 #37).
 * 기본 운영 시각은 매일 22:00~23:00 KST이고, 시각은 {@link PublicPartyTestClock}으로 고정한다.
 */
@SpringBootTest(properties = "admin.key=" + AdminPublicPartyControllerTest.ADMIN_KEY)
@AutoConfigureMockMvc
@Transactional
@Import(PublicPartyTestClock.class)
class AdminPublicPartyControllerTest {

    static final String ADMIN_KEY = "party-test-admin-key";

    /** 23:30 KST부터 90분 운영으로 바꾼 뒤 자정을 넘긴 00:50 KST */
    private static final Instant AFTER_MIDNIGHT = Instant.parse("2026-11-11T15:50:00Z");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private PublicPartyTestClock.MutableClock clock;

    @Autowired
    private PublicPartyService publicPartyService;

    @BeforeEach
    void setUp() {
        clock.set(PublicPartyTestClock.CLOSED);
    }

    /** 운영 모드는 메모리에 남으므로 다른 테스트에 영향이 없게 되돌린다 */
    @AfterEach
    void resetMode() {
        publicPartyService.changeMode(PartyMode.AUTO);
        clock.set(PublicPartyTestClock.OPEN);
    }

    private ResultActions putMode(String body) throws Exception {
        return mockMvc.perform(put("/api/admin/public-party")
                .header(AdminKeyInterceptor.ADMIN_KEY_HEADER, ADMIN_KEY)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    private ResultActions putSchedule(String body) throws Exception {
        return mockMvc.perform(put("/api/admin/public-party/schedule")
                .header(AdminKeyInterceptor.ADMIN_KEY_HEADER, ADMIN_KEY)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    /** 참가자를 만들고 토큰을 돌려준다 */
    private String participant() throws Exception {
        String body = mockMvc.perform(post("/api/participants")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nickname\":\"아현\",\"characterCode\":\"CHARACTER_01\"}"))
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.data.clientToken");
    }

    private ResultActions startWorkout(String token) throws Exception {
        return mockMvc.perform(post("/api/public-parties/workouts")
                .header(ParticipantController.TOKEN_HEADER, token));
    }

    @Test
    @DisplayName("처음에는 AUTO 모드와 설정값 운영 시각(22:00부터 60분)을 응답한다")
    void defaultState() throws Exception {
        mockMvc.perform(get("/api/admin/public-party").header(AdminKeyInterceptor.ADMIN_KEY_HEADER, ADMIN_KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resultCode").value("200-1"))
                .andExpect(jsonPath("$.data.mode").value("AUTO"))
                .andExpect(jsonPath("$.data.status").value("CLOSED"))
                .andExpect(jsonPath("$.data.openTime").value("22:00"))
                .andExpect(jsonPath("$.data.durationMinutes").value(60))
                .andExpect(jsonPath("$.data.opensAt").value("2026-11-11T22:00:00+09:00"))
                .andExpect(jsonPath("$.data.closesAt").value("2026-11-11T23:00:00+09:00"));
    }

    @Test
    @DisplayName("OPEN으로 바꾸면 운영 시각 밖에서도 열리고, closesAt은 null이며 공개 파티 운동 기록이 만들어진다")
    void openOutsideSchedule() throws Exception {
        putMode("{\"mode\":\"OPEN\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.msg").value("공개 파티 운영 모드를 바꿨습니다."))
                .andExpect(jsonPath("$.data.mode").value("OPEN"))
                .andExpect(jsonPath("$.data.status").value("OPEN"))
                .andExpect(jsonPath("$.data.opensAt").value("2026-11-11T21:00:00+09:00"))
                .andExpect(jsonPath("$.data.closesAt").value(nullValue()));

        mockMvc.perform(get("/api/public-parties/current"))
                .andExpect(jsonPath("$.data.mode").value("OPEN"))
                .andExpect(jsonPath("$.data.status").value("OPEN"))
                .andExpect(jsonPath("$.data.closesAt").value(nullValue()));
        startWorkout(participant()).andExpect(status().isCreated());
        publicPartyService.validateJoinable(1L);
    }

    @Test
    @DisplayName("이미 OPEN이면 다시 OPEN으로 바꿔도 연 시각이 그대로다")
    void openTwiceKeepsOpenedAt() throws Exception {
        putMode("{\"mode\":\"OPEN\"}");
        clock.set(PublicPartyTestClock.CLOSED.plusSeconds(600));

        putMode("{\"mode\":\"OPEN\"}")
                .andExpect(jsonPath("$.data.opensAt").value("2026-11-11T21:00:00+09:00"));
    }

    @Test
    @DisplayName("CLOSED로 바꾸면 운영 시각 안이어도 닫히고, 다음 운영 시각은 그다음 날이며 운동 기록·입장이 거부된다")
    void closeInsideSchedule() throws Exception {
        clock.set(PublicPartyTestClock.OPEN);
        String token = participant();

        putMode("{\"mode\":\"CLOSED\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.mode").value("CLOSED"))
                .andExpect(jsonPath("$.data.status").value("CLOSED"))
                .andExpect(jsonPath("$.data.opensAt").value("2026-11-12T22:00:00+09:00"))
                .andExpect(jsonPath("$.data.closesAt").value("2026-11-12T23:00:00+09:00"));

        startWorkout(token)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.resultCode").value("409-4"));
        assertThatThrownBy(() -> publicPartyService.validateJoinable(1L))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getErrorCode())
                .isEqualTo(ErrorCode.PUBLIC_PARTY_CLOSED);
    }

    @Test
    @DisplayName("닫아도 이미 시작한 공개 파티 운동 기록은 이어 하고 끝까지 저장된다")
    void closeKeepsInProgressWorkout() throws Exception {
        clock.set(PublicPartyTestClock.OPEN);
        String token = participant();
        String started = startWorkout(token).andReturn().getResponse().getContentAsString();
        Integer workoutId = JsonPath.read(started, "$.data.workoutId");

        putMode("{\"mode\":\"CLOSED\"}");

        startWorkout(token)
                .andExpect(status().is2xxSuccessful())
                .andExpect(jsonPath("$.data.workoutId").value(workoutId));
    }

    @Test
    @DisplayName("AUTO로 되돌리면 다시 운영 시각대로 판단한다")
    void backToAuto() throws Exception {
        putMode("{\"mode\":\"OPEN\"}");

        putMode("{\"mode\":\"AUTO\"}")
                .andExpect(jsonPath("$.data.mode").value("AUTO"))
                .andExpect(jsonPath("$.data.status").value("CLOSED"))
                .andExpect(jsonPath("$.data.opensAt").value("2026-11-11T22:00:00+09:00"));
    }

    @Test
    @DisplayName("운영 시각을 바꾸면 /current에 바뀐 값이 나오고, 자정을 넘겨도 열려 있다")
    void changeScheduleOverMidnight() throws Exception {
        putSchedule("{\"openTime\":\"23:30\",\"durationMinutes\":90}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.msg").value("공개 파티 운영 시각을 바꿨습니다."))
                .andExpect(jsonPath("$.data.mode").value("AUTO"))
                .andExpect(jsonPath("$.data.openTime").value("23:30"))
                .andExpect(jsonPath("$.data.durationMinutes").value(90))
                .andExpect(jsonPath("$.data.status").value("CLOSED"))
                .andExpect(jsonPath("$.data.opensAt").value("2026-11-11T23:30:00+09:00"))
                .andExpect(jsonPath("$.data.closesAt").value("2026-11-12T01:00:00+09:00"));

        clock.set(AFTER_MIDNIGHT);
        mockMvc.perform(get("/api/public-parties/current"))
                .andExpect(jsonPath("$.data.status").value("OPEN"))
                .andExpect(jsonPath("$.data.opensAt").value("2026-11-11T23:30:00+09:00"))
                .andExpect(jsonPath("$.data.closesAt").value("2026-11-12T01:00:00+09:00"));
        publicPartyService.validateJoinable(1L);
    }

    @Test
    @DisplayName("운영 시각을 두 번 바꾸면 마지막 값이 남는다")
    void changeScheduleTwice() throws Exception {
        putSchedule("{\"openTime\":\"23:30\",\"durationMinutes\":90}");

        putSchedule("{\"openTime\":\"20:00\",\"durationMinutes\":120}")
                .andExpect(jsonPath("$.data.openTime").value("20:00"))
                .andExpect(jsonPath("$.data.durationMinutes").value(120))
                .andExpect(jsonPath("$.data.status").value("OPEN"));
    }

    @Test
    @DisplayName("운영 키가 틀리면 401-1로 응답한다")
    void wrongKey() throws Exception {
        mockMvc.perform(put("/api/admin/public-party")
                        .header(AdminKeyInterceptor.ADMIN_KEY_HEADER, "wrong")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"mode\":\"OPEN\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.resultCode").value("401-1"));
        mockMvc.perform(put("/api/admin/public-party/schedule")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"openTime\":\"21:30\",\"durationMinutes\":90}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.resultCode").value("401-1"));
        mockMvc.perform(get("/api/public-parties/current"))
                .andExpect(jsonPath("$.data.mode").value("AUTO"))
                .andExpect(jsonPath("$.data.openTime").value("22:00"));
    }

    @Test
    @DisplayName("mode·openTime·durationMinutes 값이 틀리면 400으로 응답한다")
    void invalidValues() throws Exception {
        putMode("{\"mode\":\"WRONG\"}").andExpect(status().isBadRequest());
        putMode("{}").andExpect(status().isBadRequest());
        for (String body : new String[]{
                "{\"openTime\":\"25:00\",\"durationMinutes\":90}",
                "{\"openTime\":\"9:30\",\"durationMinutes\":90}",
                "{\"openTime\":\"21:30\",\"durationMinutes\":9}",
                "{\"openTime\":\"21:30\",\"durationMinutes\":721}",
                "{\"durationMinutes\":90}"}) {
            putSchedule(body).andExpect(status().isBadRequest());
        }
    }
}
