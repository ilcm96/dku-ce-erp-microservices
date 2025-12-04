package erp.approvalprocessing.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import erp.approvalprocessing.dto.ApprovalQueueItemResponse;
import erp.common.exception.CustomException;
import erp.common.exception.ErrorCode;
import erp.common.security.AuthUtil;
import erp.common.security.Role;
import erp.shared.proto.approval.ApprovalGrpc;
import erp.shared.proto.approval.ApprovalRequest;
import erp.shared.proto.approval.ApprovalResultRequest;
import erp.shared.proto.approval.ApprovalResultResponse;
import erp.shared.proto.approval.ApprovalResultStatus;
import erp.shared.proto.approval.Step;
import erp.shared.proto.approval.StepStatus;

@ExtendWith(MockitoExtension.class)
@DisplayName("ApprovalProcessingService 단위 테스트")
class ApprovalProcessingServiceTest {

    @Mock
    ApprovalQueueService approvalQueueService;

    @Mock
    AuthUtil authUtil;

    @Mock
    ApprovalGrpc.ApprovalBlockingStub approvalRequestStub;

    @InjectMocks
    ApprovalProcessingService approvalProcessingService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(approvalProcessingService, "approvalRequestMaxAttempts", 2);
        ReflectionTestUtils.setField(approvalProcessingService, "approvalRequestBackoffMillis", 1L);
    }

    @Nested
    @DisplayName("getQueue")
    class GetQueue {

        @Test
        @DisplayName("ADMIN 은 아무 approverId 나 조회할 수 있다")
        void adminCanViewAny() {
            // given
            when(authUtil.hasRole(Role.ADMIN)).thenReturn(true);
            ApprovalRequest request = approvalRequest(1L, 10L, 1, StepStatus.STEP_STATUS_PENDING);
            when(approvalQueueService.getQueue(10L)).thenReturn(List.of(request));

            // when
            List<ApprovalQueueItemResponse> result = approvalProcessingService.getQueue(10L);

            // then
            assertThat(result).hasSize(1);
            ApprovalQueueItemResponse item = result.getFirst();
            assertThat(item.requestId()).isEqualTo(1L);
            assertThat(item.title()).isEqualTo("title");
            assertThat(item.steps()).hasSize(1);
            assertThat(item.steps().getFirst().approverId()).isEqualTo(10L);
            verify(approvalQueueService).getQueue(10L);
        }

        @Test
        @DisplayName("본인 이외 approverId 조회 시 FORBIDDEN 예외가 발생한다")
        void forbidWhenNotOwner() {
            // given
            when(authUtil.hasRole(Role.ADMIN)).thenReturn(false);
            when(authUtil.currentUserId()).thenReturn(1L);

            // when & then: 예외 검증
            assertThatThrownBy(() -> approvalProcessingService.getQueue(2L))
                    .isInstanceOf(CustomException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.FORBIDDEN);
        }
    }

    @Nested
    @DisplayName("handle")
    class Handle {

        @Test
        @DisplayName("status 가 null 이면 APPROVAL_PROCESS_INVALID_STATUS 예외를 던진다")
        void invalidWhenStatusNull() {
            // given
            mockNonAdmin(1L);

            // when & then: 예외 검증
            assertThatThrownBy(() -> approvalProcessingService.handle(1L, 10L, null))
                    .isInstanceOf(CustomException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.APPROVAL_PROCESS_INVALID_STATUS);
        }

        @Test
        @DisplayName("status 가 UNSPECIFIED 여도 APPROVAL_PROCESS_INVALID_STATUS 예외를 던진다")
        void invalidWhenStatusUnspecified() {
            // given
            mockNonAdmin(1L);

            // when & then: 예외 검증
            assertThatThrownBy(() -> approvalProcessingService.handle(1L, 10L, ApprovalResultStatus.APPROVAL_RESULT_STATUS_UNSPECIFIED))
                    .isInstanceOf(CustomException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.APPROVAL_PROCESS_INVALID_STATUS);
        }

        @Test
        @DisplayName("큐에 요청이 없으면 APPROVAL_PROCESS_NOT_FOUND 예외를 던진다")
        void notFoundWhenQueueMissing() {
            // given
            mockNonAdmin(1L);
            when(approvalQueueService.getQueue(1L)).thenReturn(List.of());

            // when & then: 예외 검증
            assertThatThrownBy(() -> approvalProcessingService.handle(1L, 10L, ApprovalResultStatus.APPROVAL_RESULT_APPROVED))
                    .isInstanceOf(CustomException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.APPROVAL_PROCESS_NOT_FOUND);
        }

        @Test
        @DisplayName("approverId 에 pending step 이 없으면 APPROVAL_PROCESS_INVALID_STATUS 예외를 던진다")
        void invalidWhenPendingStepMissing() {
            // given
            mockNonAdmin(1L);
            ApprovalRequest request = approvalRequest(10L, 2L, 1, StepStatus.STEP_STATUS_PENDING);
            when(approvalQueueService.getQueue(1L)).thenReturn(List.of(request));
            when(approvalQueueService.remove(1L, 10L)).thenReturn(request);

            // when & then: 예외 검증
            assertThatThrownBy(() -> approvalProcessingService.handle(1L, 10L, ApprovalResultStatus.APPROVAL_RESULT_APPROVED))
                    .isInstanceOf(CustomException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.APPROVAL_PROCESS_INVALID_STATUS);
        }

        @Test
        @DisplayName("큐의 맨 앞이 아닌 요청을 처리하려 하면 APPROVAL_PROCESS_INVALID_STATUS 예외를 던진다")
        void invalidWhenNotHeadOfQueue() {
            // given
            mockNonAdmin(1L);
            ApprovalRequest head = approvalRequest(10L, 1L, 1, StepStatus.STEP_STATUS_PENDING);
            ApprovalRequest later = approvalRequest(20L, 1L, 1, StepStatus.STEP_STATUS_PENDING);
            when(approvalQueueService.getQueue(1L)).thenReturn(List.of(head, later));

            // when & then: 예외 검증
            assertThatThrownBy(() -> approvalProcessingService.handle(1L, 20L, ApprovalResultStatus.APPROVAL_RESULT_APPROVED))
                    .isInstanceOf(CustomException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.APPROVAL_PROCESS_INVALID_STATUS);

            verify(approvalQueueService, times(0)).remove(1L, 20L);
        }

        @Test
        @DisplayName("정상 처리 시 ApprovalResultRequest 를 생성해 gRPC 콜백을 호출한다")
        void handleSuccess() {
            // given
            mockNonAdmin(1L);
            ApprovalRequest request = approvalRequest(10L, 1L, 2, StepStatus.STEP_STATUS_PENDING);
            when(approvalQueueService.getQueue(1L)).thenReturn(List.of(request));
            when(approvalQueueService.remove(1L, 10L)).thenReturn(request);
            when(approvalRequestStub.returnApprovalResult(any())).thenReturn(
                    ApprovalResultResponse.newBuilder().setStatus("accepted").build());

            // when
            approvalProcessingService.handle(1L, 10L, ApprovalResultStatus.APPROVAL_RESULT_APPROVED);

            // then
            ArgumentCaptor<ApprovalResultRequest> captor = ArgumentCaptor.forClass(ApprovalResultRequest.class);
            verify(approvalQueueService).remove(1L, 10L);
            verify(approvalRequestStub).returnApprovalResult(captor.capture());

            ApprovalResultRequest sent = captor.getValue();
            assertThat(sent.getRequestId()).isEqualTo(10L);
            assertThat(sent.getApproverId()).isEqualTo(1L);
            assertThat(sent.getStep()).isEqualTo(2);
            assertThat(sent.getStatus()).isEqualTo(ApprovalResultStatus.APPROVAL_RESULT_APPROVED);
        }

        @Test
        @DisplayName("gRPC 콜백 실패 시 설정된 횟수만큼 재시도한다")
        void retryWhenGrpcFails() {
            // given
            mockNonAdmin(1L);
            ApprovalRequest request = approvalRequest(11L, 1L, 1, StepStatus.STEP_STATUS_PENDING);
            when(approvalQueueService.getQueue(1L)).thenReturn(List.of(request));
            when(approvalQueueService.remove(1L, 11L)).thenReturn(request);

            doThrow(new RuntimeException("first fail"))
                    .doReturn(ApprovalResultResponse.newBuilder().setStatus("accepted").build())
                    .when(approvalRequestStub)
                    .returnApprovalResult(any());

            // when
            approvalProcessingService.handle(1L, 11L, ApprovalResultStatus.APPROVAL_RESULT_REJECTED);

            // then
            verify(approvalRequestStub, times(2)).returnApprovalResult(any());
        }
    }

    @Nested
    @DisplayName("acceptRequest / acceptResult")
    class Accepts {

        @Test
        @DisplayName("acceptRequest 는 큐에 요청을 추가한다")
        void acceptRequest() {
            // given
            ApprovalRequest request = approvalRequest(1L, 5L, 1, StepStatus.STEP_STATUS_PENDING);

            // when
            approvalProcessingService.acceptRequest(request);

            // then
            verify(approvalQueueService).enqueue(request);
        }

        @Test
        @DisplayName("acceptResult 는 approverId/requestId 기준으로 큐에서 제거한다")
        void acceptResult() {
            // given
            ApprovalResultRequest request = ApprovalResultRequest.newBuilder()
                    .setRequestId(1L)
                    .setApproverId(3L)
                    .build();

            // when
            approvalProcessingService.acceptResult(request);

            // then
            verify(approvalQueueService).remove(3L, 1L);
        }
    }

    private void mockNonAdmin(long userId) {
        when(authUtil.hasRole(Role.ADMIN)).thenReturn(false);
        when(authUtil.currentUserId()).thenReturn(userId);
    }

    private ApprovalRequest approvalRequest(long requestId, long approverId, int step, StepStatus status) {
        Step pendingStep = Step.newBuilder()
                .setStep(step)
                .setApproverId(approverId)
                .setStatus(status)
                .build();

        return ApprovalRequest.newBuilder()
                .setRequestId(requestId)
                .setRequesterId(99L)
                .setTitle("title")
                .setContent("content")
                .addSteps(pendingStep)
                .build();
    }
}
