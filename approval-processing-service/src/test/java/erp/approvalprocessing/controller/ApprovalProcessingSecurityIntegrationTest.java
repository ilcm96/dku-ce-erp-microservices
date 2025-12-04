package erp.approvalprocessing.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import erp.approvalprocessing.support.ApprovalProcessingIntegrationTestSupport;

@DisplayName("ApprovalProcessing 보안 통합 테스트")
class ApprovalProcessingSecurityIntegrationTest extends ApprovalProcessingIntegrationTestSupport {

    @Nested
    @DisplayName("queue API")
    class QueueApi {

        @Test
        @DisplayName("토큰 없이 요청하면 401(AUTH_TOKEN_MISSING)을 반환한다")
        void unauthorizedWhenTokenMissing() throws Exception {
            mockMvc.perform(get("/process/{approverId}", 1L))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.errorCodeName").value("AUTH_TOKEN_MISSING"));
        }

        @Test
        @DisplayName("EMPLOYEE 토큰으로 조회하면 403(FORBIDDEN)을 반환한다")
        void forbidWhenRoleNotAllowed() throws Exception {
            mockMvc.perform(get("/process/{approverId}", 1L)
                            .header("X-User-Id", "1")
                            .header("X-User-Roles", "EMPLOYEE"))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.errorCodeName").value("FORBIDDEN"));
        }
    }

    @Nested
    @DisplayName("handle API")
    class HandleApi {

        @Test
        @DisplayName("권한이 없는 사용자가 처리 요청하면 403을 반환한다")
        void forbidHandleWhenRoleNotAllowed() throws Exception {
            mockMvc.perform(post("/process/{approverId}/{requestId}", 1L, 10L)
                            .header("X-User-Id", "2")
                            .header("X-User-Roles", "EMPLOYEE")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"status":"APPROVAL_RESULT_APPROVED"}
                                    """))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.errorCodeName").value("FORBIDDEN"));
        }
    }
}
