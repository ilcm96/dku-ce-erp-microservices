package erp.approvalprocessing.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import erp.shared.proto.approval.ApprovalRequest;
import erp.shared.proto.approval.Step;
import erp.shared.proto.approval.StepStatus;

@DisplayName("ApprovalQueueService 단위 테스트")
class ApprovalQueueServiceTest {

    private ApprovalQueueService approvalQueueService;

    @BeforeEach
    void setUp() {
        approvalQueueService = new ApprovalQueueService();
    }

    @Nested
    @DisplayName("enqueue")
    class Enqueue {

        @Test
        @DisplayName("pending 단계가 없으면 큐에 추가하지 않는다")
        void skipWhenNoPendingStep() {
            // given
            ApprovalRequest request = approvalRequest(1L, 10L, 1, StepStatus.STEP_STATUS_APPROVED);

            // when
            approvalQueueService.enqueue(request);

            // then
            assertThat(approvalQueueService.getQueue(10L)).isEmpty();
        }

        @Test
        @DisplayName("pending step 번호 → requestId 순으로 정렬한다")
        void sortByPendingStepThenRequestId() {
            // given
            ApprovalRequest request2 = approvalRequest(2L, 10L, 2, StepStatus.STEP_STATUS_PENDING);
            ApprovalRequest request3 = approvalRequest(3L, 10L, 1, StepStatus.STEP_STATUS_PENDING);
            ApprovalRequest request1 = approvalRequest(1L, 10L, 1, StepStatus.STEP_STATUS_PENDING);

            // when
            approvalQueueService.enqueue(request2);
            approvalQueueService.enqueue(request3);
            approvalQueueService.enqueue(request1);

            // then
            List<ApprovalRequest> queue = approvalQueueService.getQueue(10L);
            assertThat(queue).extracting(ApprovalRequest::getRequestId)
                    .containsExactly(1L, 3L, 2L);
        }

        @Test
        @DisplayName("같은 requestId 재등록 시 기존 항목을 교체한다")
        void replaceWhenSameRequestId() {
            // given
            ApprovalRequest original = approvalRequest(5L, 10L, 2, StepStatus.STEP_STATUS_PENDING);
            ApprovalRequest updated = approvalRequest(5L, 10L, 1, StepStatus.STEP_STATUS_PENDING);

            approvalQueueService.enqueue(original);

            // when
            approvalQueueService.enqueue(updated);

            // then
            List<ApprovalRequest> queue = approvalQueueService.getQueue(10L);
            assertThat(queue).hasSize(1);
            assertThat(queue.getFirst()).isEqualTo(updated);
        }

        @Test
        @DisplayName("getQueue 는 방어적 복사본을 반환한다")
        void getQueueReturnsDefensiveCopy() {
            // given
            ApprovalRequest request = approvalRequest(7L, 20L, 1, StepStatus.STEP_STATUS_PENDING);
            approvalQueueService.enqueue(request);

            // when
            List<ApprovalRequest> queue = approvalQueueService.getQueue(20L);

            // then
            assertThat(queue).hasSize(1);
            assertThatExceptionOfType(UnsupportedOperationException.class)
                    .isThrownBy(() -> queue.add(request));
            assertThat(approvalQueueService.getQueue(20L)).hasSize(1);
        }
    }

    @Nested
    @DisplayName("remove")
    class Remove {

        @Test
        @DisplayName("일치하는 요청을 제거하고 반환한다")
        void removeExistingRequest() {
            // given
            ApprovalRequest request = approvalRequest(9L, 30L, 1, StepStatus.STEP_STATUS_PENDING);
            approvalQueueService.enqueue(request);

            // when
            ApprovalRequest removed = approvalQueueService.remove(30L, 9L);

            // then
            assertThat(removed).isEqualTo(request);
            assertThat(approvalQueueService.getQueue(30L)).isEmpty();
        }

        @Test
        @DisplayName("없는 요청을 제거하면 null 을 반환한다")
        void returnNullWhenNotFound() {
            // when
            ApprovalRequest removed = approvalQueueService.remove(40L, 999L);

            // then
            assertThat(removed).isNull();
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
                .addSteps(pendingStep)
                .build();
    }
}
