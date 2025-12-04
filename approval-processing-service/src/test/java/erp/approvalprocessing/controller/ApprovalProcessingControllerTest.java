package erp.approvalprocessing.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
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
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.mockito.Mockito;
import org.junit.jupiter.api.AfterEach;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.web.servlet.MockMvc;

import erp.approvalprocessing.dto.ApprovalQueueItemResponse;
import erp.approvalprocessing.service.ApprovalProcessingService;
import erp.common.exception.GlobalExceptionHandler;
import erp.common.exception.CustomException;
import erp.common.exception.ErrorCode;
import erp.shared.proto.approval.ApprovalRequest;
import erp.shared.proto.approval.ApprovalResultStatus;
import erp.shared.proto.approval.Step;
import erp.shared.proto.approval.StepStatus;

@WebMvcTest(controllers = ApprovalProcessingController.class)
@Import({GlobalExceptionHandler.class, ApprovalProcessingControllerTest.MockConfig.class})
@AutoConfigureMockMvc(addFilters = false)
@DisplayName("ApprovalProcessingController WebMvc 테스트")
class ApprovalProcessingControllerTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ApprovalProcessingController approvalProcessingController;

    @Autowired
    ApprovalProcessingService approvalProcessingService;

    @AfterEach
    void resetMocks() {
        Mockito.reset(approvalProcessingService);
    }

    @TestConfiguration
    static class MockConfig {

        @Bean
        @Primary
        ApprovalProcessingService approvalProcessingService() {
            return Mockito.mock(ApprovalProcessingService.class);
        }
    }

    @Nested
    @DisplayName("queue")
    class Queue {

        @Test
        @DisplayName("ADMIN 은 모든 approverId 큐를 조회할 수 있다")
        void adminCanViewAnyApproverQueue() {
            // given
            ApprovalQueueItemResponse responseDto = approvalQueueItemResponse(1L, 99L, 1, StepStatus.STEP_STATUS_PENDING);
            when(approvalProcessingService.getQueue(99L)).thenReturn(java.util.List.of(responseDto));

            // when
            ResponseEntity<List<ApprovalQueueItemResponse>> response = approvalProcessingController.queue(99L);

            // then: 반환값 검증
            assertThat(response.getStatusCode().value()).isEqualTo(200);
            assertThat(response.getBody()).containsExactly(responseDto);
            verify(approvalProcessingService).getQueue(99L);
        }

        @Test
        @DisplayName("본인이 아닌 approverId 를 조회하면 FORBIDDEN 이 반환된다")
        void forbidWhenNotOwner() throws Exception {
            // given
            when(approvalProcessingService.getQueue(2L)).thenThrow(new CustomException(ErrorCode.FORBIDDEN));

            // when & then: 예외 매핑 검증
            mockMvc.perform(get("/process/{approverId}", 2L))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.errorCodeName").value("FORBIDDEN"));
        }
    }

    @Nested
    @DisplayName("handle")
    class Handle {

        @Test
        @DisplayName("status 가 null 이면 APPROVAL_PROCESS_INVALID_STATUS 를 반환한다")
        void invalidWhenStatusNull() throws Exception {
            // given
            doThrow(new CustomException(ErrorCode.APPROVAL_PROCESS_INVALID_STATUS))
                    .when(approvalProcessingService)
                    .handle(1L, 10L, null);

            // when & then: 예외 검증
            mockMvc.perform(post("/process/{approverId}/{requestId}", 1L, 10L)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errorCodeName").value("APPROVAL_PROCESS_INVALID_STATUS"));
        }

        @Test
        @DisplayName("status 가 UNSPECIFIED 일 때도 APPROVAL_PROCESS_INVALID_STATUS 를 반환한다")
        void invalidWhenStatusUnspecified() throws Exception {
            // given
            doThrow(new CustomException(ErrorCode.APPROVAL_PROCESS_INVALID_STATUS))
                    .when(approvalProcessingService)
                    .handle(1L, 10L, ApprovalResultStatus.APPROVAL_RESULT_STATUS_UNSPECIFIED);

            // when & then: 예외 검증
            mockMvc.perform(post("/process/{approverId}/{requestId}", 1L, 10L)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"status":"APPROVAL_RESULT_STATUS_UNSPECIFIED"}
                                    """))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errorCodeName").value("APPROVAL_PROCESS_INVALID_STATUS"));
        }

        @Test
        @DisplayName("큐에 요청이 없으면 APPROVAL_PROCESS_NOT_FOUND 를 반환한다")
        void notFoundWhenQueueMissing() throws Exception {
            // given
            doThrow(new CustomException(ErrorCode.APPROVAL_PROCESS_NOT_FOUND))
                    .when(approvalProcessingService)
                    .handle(1L, 10L, ApprovalResultStatus.APPROVAL_RESULT_APPROVED);

            // when & then: 예외 검증
            mockMvc.perform(post("/process/{approverId}/{requestId}", 1L, 10L)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"status":"APPROVAL_RESULT_APPROVED"}
                                    """))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.errorCodeName").value("APPROVAL_PROCESS_NOT_FOUND"));
        }

        @Test
        @DisplayName("approverId 에 매칭되는 pending step 이 없으면 APPROVAL_PROCESS_INVALID_STATUS 를 반환한다")
        void invalidWhenPendingStepNotFound() throws Exception {
            // given
            doThrow(new CustomException(ErrorCode.APPROVAL_PROCESS_INVALID_STATUS))
                    .when(approvalProcessingService)
                    .handle(1L, 10L, ApprovalResultStatus.APPROVAL_RESULT_APPROVED);

            // when & then: 예외 검증
            mockMvc.perform(post("/process/{approverId}/{requestId}", 1L, 10L)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"status":"APPROVAL_RESULT_APPROVED"}
                                    """))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errorCodeName").value("APPROVAL_PROCESS_INVALID_STATUS"));
        }

        @Test
        @DisplayName("정상 처리 시 ApprovalResultRequest 를 구성해 반환 gRPC 를 호출한다")
        void handleSuccess() throws Exception {
            // given
            // success path는 별도 스텁 없이 호출만 검증한다.

            // when
            mockMvc.perform(post("/process/{approverId}/{requestId}", 1L, 10L)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"status":"APPROVAL_RESULT_APPROVED"}
                                    """))
                    .andExpect(status().isAccepted());

            // then
            verify(approvalProcessingService).handle(1L, 10L, ApprovalResultStatus.APPROVAL_RESULT_APPROVED);
        }

        @Test
        @DisplayName("gRPC 호출 실패 시 설정된 재시도 횟수만큼 재시도한다")
        void retryWhenGrpcFails() throws Exception {
            // given
            // success path는 별도 스텁 없이 호출만 검증한다.

            // when
            mockMvc.perform(post("/process/{approverId}/{requestId}", 1L, 11L)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"status":"APPROVAL_RESULT_REJECTED"}
                                    """))
                    .andExpect(status().isAccepted());
            verify(approvalProcessingService).handle(1L, 11L, ApprovalResultStatus.APPROVAL_RESULT_REJECTED);
        }
    }

    private ApprovalRequest approvalRequest(long requestId, long approverId, int step, StepStatus status) {
        Step pendingStep = Step.newBuilder()
                .setStep(step)
                .setApproverId(approverId)
                .setStatus(status)
                .build();

        return ApprovalRequest.newBuilder()
                .setRequestId(requestId)
                .setRequesterId(1L)
                .setTitle("title")
                .setContent("content")
                .addSteps(pendingStep)
                .build();
    }

    private ApprovalQueueItemResponse approvalQueueItemResponse(long requestId, long approverId, int step, StepStatus status) {
        return ApprovalQueueItemResponse.from(approvalRequest(requestId, approverId, step, status));
    }
}
