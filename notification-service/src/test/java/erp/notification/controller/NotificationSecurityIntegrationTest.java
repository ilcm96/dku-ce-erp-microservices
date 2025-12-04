package erp.notification.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import com.fasterxml.jackson.databind.ObjectMapper;

import erp.notification.controller.NotificationController.NotifyRequest;
import erp.notification.support.NotificationIntegrationTestSupport;

@AutoConfigureMockMvc
@DisplayName("Notification 보안 통합 테스트")
class NotificationSecurityIntegrationTest extends NotificationIntegrationTestSupport {

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private MockMvc mockMvc;

    @Nested
    class Authorization {

        @Test
        @DisplayName("토큰 없이 요청하면 401(AUTH_TOKEN_MISSING)을 반환한다")
        void unauthorizedWhenTokenMissing() throws Exception {
            mockMvc.perform(post("/notifications/{employeeId}", 1L)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(new NotifyRequest("hello"))))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.errorCodeName").value("AUTH_TOKEN_MISSING"));
        }

        @Test
        @DisplayName("EMPLOYEE 토큰으로 요청하면 403(FORBIDDEN)을 반환한다")
        void forbidWhenRoleNotAdmin() throws Exception {
            mockMvc.perform(post("/notifications/{employeeId}", 1L)
                            .header("X-User-Id", "10")
                            .header("X-User-Roles", "EMPLOYEE")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(new NotifyRequest("hello"))))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.errorCodeName").value("FORBIDDEN"));
        }
    }
}
