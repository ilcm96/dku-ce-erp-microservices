package erp.employee.controller;

import static org.hamcrest.Matchers.containsInRelativeOrder;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import com.fasterxml.jackson.databind.ObjectMapper;

import erp.common.security.Role;
import erp.employee.EmployeeIntegrationTestSupport;

@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
@DisplayName("AuthController")
class AuthControllerTest extends EmployeeIntegrationTestSupport {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @DisplayName("로그인 성공 시 JWT와 사용자 정보를 반환한다")
    void login_success() throws Exception {
        // given
        saveEmployee("user@example.com", "관리자", "본사", "팀장", Role.ADMIN, "password123!");
        String body = objectMapper.writeValueAsString(Map.of(
                "email", "user@example.com",
                "password", "password123!"));

        // when & then
        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andExpect(jsonPath("$.tokenType", is("Bearer")))
                .andExpect(jsonPath("$.userId").isNumber())
                .andExpect(jsonPath("$.roles", containsInRelativeOrder(
                        Role.EMPLOYEE.name(),
                        Role.APPROVER.name(),
                        Role.ADMIN.name())))
                .andExpect(jsonPath("$.expiresAt").isNotEmpty());
    }

    @Test
    @DisplayName("잘못된 자격 증명이면 401을 반환한다")
    void login_unauthorized() throws Exception {
        // given
        String body = objectMapper.writeValueAsString(Map.of(
                "email", "unknown@example.com",
                "password", "wrong"));

        // when & then
        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCodeName", is("UNAUTHORIZED")));
    }
}
