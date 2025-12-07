package erp.approvalrequest.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import erp.approvalrequest.ApprovalRequestIntegrationTestSupport;
import erp.shared.proto.approval.StepStatus;

@AutoConfigureMockMvc
@DisplayName("ApprovalRequest 보안 통합 테스트")
class ApprovalSecurityIntegrationTest extends ApprovalRequestIntegrationTestSupport {

    @Autowired
    private MockMvc mockMvc;

    @Nested
    @DisplayName("인증 누락")
    class Unauthorized {

        @Test
        @DisplayName("토큰 없이 목록 조회하면 401(AUTH_TOKEN_MISSING)을 반환한다")
        void listUnauthorizedWhenTokenMissing() throws Exception {
            mockMvc.perform(get("/approvals"))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.errorCodeName").value("AUTH_TOKEN_MISSING"));
        }
    }

    @Nested
    @DisplayName("권한 부족")
    class Forbidden {

        @Test
        @DisplayName("권한이 없는 토큰으로 재전송을 호출하면 403(FORBIDDEN)을 반환한다")
        void resendForbiddenWhenRoleMissing() throws Exception {
            saveDocument(1L, List.of(step(1, 10L, StepStatus.STEP_STATUS_PENDING)), StepStatus.STEP_STATUS_PENDING);

            mockMvc.perform(post("/approvals/{id}/resend", 1L)
                            .header("X-User-Id", "5")
                            .header("X-User-Roles", "EMPLOYEE"))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.errorCodeName").value("FORBIDDEN"));
        }
    }
}
