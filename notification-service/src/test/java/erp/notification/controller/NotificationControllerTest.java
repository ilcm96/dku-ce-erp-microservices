package erp.notification.controller;

import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.mockito.Mockito;

import com.fasterxml.jackson.databind.ObjectMapper;

import erp.notification.config.WebSocketConfig.SessionStore;
import erp.notification.controller.NotificationController.NotifyRequest;
import erp.notification.support.NotificationIntegrationTestSupport;

@AutoConfigureMockMvc
@DisplayName("NotificationController 통합 테스트")
@Import(NotificationControllerTest.MockConfig.class)
class NotificationControllerTest extends NotificationIntegrationTestSupport {

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private SessionStore sessionStore;

    @TestConfiguration
    static class MockConfig {

        @Bean
        @Primary
        SessionStore sessionStore() {
            return Mockito.mock(SessionStore.class);
        }
    }

    @Nested
    class Send {

        @Test
        @DisplayName("유효한 요청이면 202와 함께 SessionStore.sendTo 를 호출한다")
        void sendNotification() throws Exception {
            // given
            NotifyRequest request = new NotifyRequest("hello");

            // when & then: 반환값 검증
            mockMvc.perform(post("/notifications/{employeeId}", 1L)
                            .header("X-User-Id", "1")
                            .header("X-User-Roles", "ADMIN")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isAccepted());

            verify(sessionStore).sendTo(1L, "hello");
        }

        @Test
        @DisplayName("payload 가 비어 있으면 400과 BAD_REQUEST 에러를 반환한다")
        void rejectBlankPayload() throws Exception {
            // when & then: 예외 검증
            mockMvc.perform(post("/notifications/{employeeId}", 1L)
                            .header("X-User-Id", "1")
                            .header("X-User-Roles", "ADMIN")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{ \"payload\": \"\" }"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errorCodeName").value("BAD_REQUEST"))
                    .andExpect(jsonPath("$.errors[0].field").value("payload"));
        }
    }
}
