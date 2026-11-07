package com.team20.fitmate.global.exception;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * {@link GlobalExceptionHandler}가 예외를 RsData 형식의 응답으로 바꾸는지 확인하는 테스트.
 * 스프링 컨텍스트 없이 테스트용 컨트롤러 하나만 올려서 검증한다.
 */
class GlobalExceptionHandlerTest {

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new TestController())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    @DisplayName("CustomException은 ErrorCode의 HTTP 상태와 resultCode·msg로 응답한다")
    void customException() throws Exception {
        mockMvc.perform(get("/test/custom"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.resultCode").value("400-1"))
                .andExpect(jsonPath("$.msg").value(ErrorCode.INVALID_INPUT.getMsg()))
                .andExpect(jsonPath("$.data").doesNotExist());
    }

    @Test
    @DisplayName("@Valid 검증 실패는 400-1과 필드별 오류 목록으로 응답한다")
    void validationFail() throws Exception {
        mockMvc.perform(post("/test/valid")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nickname\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.resultCode").value("400-1"))
                .andExpect(jsonPath("$.data[0].field").value("nickname"));
    }

    @Test
    @DisplayName("JSON 형식이 깨진 요청 본문은 400-2로 응답한다")
    void notReadable() throws Exception {
        mockMvc.perform(post("/test/valid")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{깨진 json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.resultCode").value("400-2"));
    }

    @Test
    @DisplayName("필수 파라미터 누락은 400-3으로 응답한다")
    void missingParameter() throws Exception {
        mockMvc.perform(get("/test/param"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.resultCode").value("400-3"));
    }

    @Test
    @DisplayName("지원하지 않는 HTTP 메서드는 405-1로 응답한다")
    void methodNotAllowed() throws Exception {
        mockMvc.perform(post("/test/custom"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.resultCode").value("405-1"));
    }

    @Test
    @DisplayName("처리하지 못한 예외는 500-1로 응답하고 내부 메시지를 노출하지 않는다")
    void unknownException() throws Exception {
        mockMvc.perform(get("/test/unknown"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.resultCode").value("500-1"))
                .andExpect(jsonPath("$.msg").value(ErrorCode.INTERNAL_SERVER_ERROR.getMsg()))
                .andExpect(content().string(not(containsString("내부 비밀 메시지"))));
    }

    /**
     * 테스트에서만 쓰는 컨트롤러. 각 엔드포인트가 특정 예외를 일으킨다.
     */
    @RestController
    static class TestController {

        /** CustomException을 던진다 */
        @GetMapping("/test/custom")
        public String custom() {
            throw new CustomException(ErrorCode.INVALID_INPUT);
        }

        /** 요청 본문을 @Valid로 검증한다 */
        @PostMapping("/test/valid")
        public String valid(@Valid @RequestBody TestRequest request) {
            return "ok";
        }

        /** 필수 파라미터 name을 받는다 */
        @GetMapping("/test/param")
        public String param(@RequestParam("name") String name) {
            return name;
        }

        /** 예상하지 못한 예외를 던진다 */
        @GetMapping("/test/unknown")
        public String unknown() {
            throw new IllegalStateException("내부 비밀 메시지");
        }
    }

    /**
     * 검증 테스트용 요청 본문.
     *
     * @param nickname 비어 있으면 안 되는 값
     */
    record TestRequest(@NotBlank String nickname) {
    }
}
