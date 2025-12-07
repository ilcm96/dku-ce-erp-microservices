package erp.approvalprocessing.service;

import java.util.Comparator;
import java.util.List;

import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import erp.approvalprocessing.dto.ApprovalQueueItemResponse;
import erp.common.exception.CustomException;
import erp.common.exception.ErrorCode;
import erp.common.security.AuthUtil;
import erp.common.security.Role;
import erp.common.messaging.ApprovalMessagingConstants;
import erp.shared.proto.approval.ApprovalRequest;
import erp.shared.proto.approval.ApprovalResultRequest;
import erp.shared.proto.approval.ApprovalResultStatus;
import erp.shared.proto.approval.Step;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ApprovalProcessingService {

    private final ApprovalQueueService approvalQueueService;
    private final AuthUtil authUtil;
    private final RabbitTemplate rabbitTemplate;

    @Value("${approval-request.retry.max-attempts}")
    private int approvalRequestMaxAttempts;

    @Value("${approval-request.retry.backoff-millis}")
    private long approvalRequestBackoffMillis;

    public List<ApprovalQueueItemResponse> getQueue(Long approverId) {
        enforceAccess(approverId);
        List<ApprovalRequest> queue = approvalQueueService.getQueue(approverId);
        return ApprovalQueueItemResponse.fromList(queue);
    }

    public void handle(Long approverId, Long requestId, ApprovalResultStatus status) {
        enforceAccess(approverId);
        validateStatus(status);

        List<ApprovalRequest> queueSnapshot = approvalQueueService.getQueue(approverId);
        if (queueSnapshot.isEmpty()) {
            throw new CustomException(ErrorCode.APPROVAL_PROCESS_NOT_FOUND);
        }
        ApprovalRequest head = queueSnapshot.getFirst();
        if (head.getRequestId() != requestId) {
            throw new CustomException(ErrorCode.APPROVAL_PROCESS_INVALID_STATUS);
        }

        ApprovalRequest queued = approvalQueueService.remove(approverId, requestId);
        if (queued == null) {
            throw new CustomException(ErrorCode.APPROVAL_PROCESS_NOT_FOUND);
        }

        Step targetStep = findPendingStep(queued, approverId);

        ApprovalResultRequest resultRequest = ApprovalResultRequest.newBuilder()
                .setApproverId(approverId)
                .setRequestId(requestId)
                .setStep(targetStep.getStep())
                .setStatus(status)
                .build();

        callReturnWithRetry(resultRequest);
    }

    public void acceptRequest(ApprovalRequest request) {
        approvalQueueService.enqueue(request);
    }

    private void validateStatus(ApprovalResultStatus status) {
        if (status == null || status == ApprovalResultStatus.APPROVAL_RESULT_STATUS_UNSPECIFIED) {
            throw new CustomException(ErrorCode.APPROVAL_PROCESS_INVALID_STATUS);
        }
    }

    private Step findPendingStep(ApprovalRequest queued, Long approverId) {
        return queued.getStepsList().stream()
                .filter(s -> s.getStatus() == erp.shared.proto.approval.StepStatus.STEP_STATUS_PENDING)
                .filter(s -> s.getApproverId() == approverId)
                .min(Comparator.comparingInt(Step::getStep))
                .orElseThrow(() -> new CustomException(ErrorCode.APPROVAL_PROCESS_INVALID_STATUS));
    }

    private void enforceAccess(Long approverId) {
        if (authUtil.hasRole(Role.ADMIN)) {
            return;
        }
        if (!authUtil.currentUserId().equals(approverId)) {
            throw new CustomException(ErrorCode.FORBIDDEN);
        }
    }

    private void callReturnWithRetry(ApprovalResultRequest request) {
        for (int attempt = 1; attempt <= Math.max(1, approvalRequestMaxAttempts); attempt++) {
            try {
                rabbitTemplate.convertAndSend(
                        ApprovalMessagingConstants.EXCHANGE_NAME,
                        ApprovalMessagingConstants.ROUTING_KEY_RESULT,
                        request.toByteArray());
                return;
            } catch (Exception e) {
                if (attempt == approvalRequestMaxAttempts) {
                    throw e;
                }
                try {
                    Thread.sleep(approvalRequestBackoffMillis);
                } catch (InterruptedException ignored) {
                    Thread.currentThread().interrupt();
                }
            }
        }
    }
}
