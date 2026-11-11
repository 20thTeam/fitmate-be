package com.team20.fitmate.domain.admin;

import com.team20.fitmate.domain.admin.auth.AdminKeyInterceptor;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 서버에 운영 키(ADMIN_KEY)를 설정하지 않으면 어떤 헤더를 보내도 운영 API가 열리지 않는지 확인한다.
 * 참가자용 API는 영향을 받지 않는다.
 */
@SpringBootTest
@AutoConfigureMockMvc
class AdminDisabledTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("운영 키가 설정되지 않으면 헤더가 있어도 403-1로 막고, 참가자용 API는 그대로 동작한다")
    void disabledWithoutServerKey() throws Exception {
        mockMvc.perform(get("/api/admin/participants"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.resultCode").value("403-1"));
        mockMvc.perform(post("/api/admin/reset").header(AdminKeyInterceptor.ADMIN_KEY_HEADER, ""))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.resultCode").value("403-1"));
        mockMvc.perform(get("/api/admin/rooms").header(AdminKeyInterceptor.ADMIN_KEY_HEADER, "anything"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.resultCode").value("403-1"));

        mockMvc.perform(get("/api/rooms"))
                .andExpect(status().isOk());
    }
}
