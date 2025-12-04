package erp.approvalrequest.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.hamcrest.Matchers.hasItem;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.mockito.Mockito;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import com.fasterxml.jackson.databind.ObjectMapper;

import erp.approvalrequest.ApprovalRequestIntegrationTestSupport;
import erp.approvalrequest.dto.ApprovalCreateRequest;
import erp.approvalrequest.dto.ApprovalResponse;
import erp.approvalrequest.service.ApprovalRequestService;
import erp.common.exception.CustomException;
import erp.common.exception.ErrorCode;
import erp.shared.proto.approval.StepStatus;

@AutoConfigureMockMvc
@Import(ApprovalControllerTest.MockConfig.class)
class ApprovalControllerTest extends ApprovalRequestIntegrationTestSupport {

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ApprovalRequestService approvalRequestService;

    @TestConfiguration
    static class MockConfig {

        @Bean
        @Primary
        ApprovalRequestService approvalRequestService() {
            return Mockito.mock(ApprovalRequestService.class);
        }
    }

    @Nested
    class Create {

        @Test
        void 승인_요청을_생성하면_201과_응답_DTO를_반환한다() throws Exception {
            // given
            ApprovalCreateRequest request = new ApprovalCreateRequest(
                    "출장 결재", "3일 일정", List.of(new ApprovalCreateRequest.StepDto(1, 10L)));

            ApprovalResponse response = new ApprovalResponse(
                    100L,
                    "doc-id",
                    1L,
                    request.title(),
                    request.content(),
                    List.of(new ApprovalResponse.StepResponse(1, 10L, StepStatus.STEP_STATUS_PENDING)),
                    StepStatus.STEP_STATUS_PENDING,
                    Instant.now(),
                    Instant.now());

            given(approvalRequestService.create(any())).willReturn(response);

            // when & then: 반환값 검증
            mockMvc.perform(post("/approvals")
                            .header("X-User-Id", "1")
                            .header("X-User-Roles", "EMPLOYEE")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.requestId").value(100))
                    .andExpect(jsonPath("$.steps[0].approverId").value(10));
        }

        @Test
        void 필수값이_누락되면_400과_BAD_REQUEST를_반환한다() throws Exception {
            // given
            String invalidPayload = "{ \"content\": \"내용만\" }";

            // when & then: 예외 검증
            mockMvc.perform(post("/approvals")
                            .header("X-User-Id", "1")
                            .header("X-User-Roles", "EMPLOYEE")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(invalidPayload))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errorCodeName").value("BAD_REQUEST"))
                    .andExpect(jsonPath("$.errors[*].field", hasItem("title")));
        }

        @Test
        void 서비스에서_CustomException이_발생하면_매핑된_status와_errorCode를_반환한다() throws Exception {
            // given
            ApprovalCreateRequest request = new ApprovalCreateRequest(
                    "제목", "내용", List.of(new ApprovalCreateRequest.StepDto(1, 1L)));
            given(approvalRequestService.create(any()))
                    .willThrow(new CustomException(ErrorCode.APPROVAL_REQUEST_INVALID_STEP));

            // when & then: 예외 검증
            mockMvc.perform(post("/approvals")
                            .header("X-User-Id", "1")
                            .header("X-User-Roles", "EMPLOYEE")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errorCodeName").value("APPROVAL_REQUEST_INVALID_STEP"));
        }
    }

    @Nested
    class Get {

        @Test
        void 리스트_조회시_서비스_결과를_반환한다() throws Exception {
            // given
            ApprovalResponse response = new ApprovalResponse(
                    200L,
                    "doc-id",
                    1L,
                    "제목",
                    "내용",
                    List.of(new ApprovalResponse.StepResponse(1, 10L, StepStatus.STEP_STATUS_PENDING)),
                    StepStatus.STEP_STATUS_PENDING,
                    Instant.now(),
                    Instant.now());
            given(approvalRequestService.listForCurrentUser()).willReturn(List.of(response));

            // when & then: 반환값 검증
            mockMvc.perform(get("/approvals")
                            .header("X-User-Id", "1")
                            .header("X-User-Roles", "EMPLOYEE"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[0].requestId").value(200));
        }
    }
}
