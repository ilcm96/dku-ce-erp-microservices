package erp.approvalrequest.service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.dao.OptimisticLockingFailureException;

import erp.approvalrequest.client.EmployeeClient;
import erp.approvalrequest.client.NotificationClient;
import erp.approvalrequest.domain.ApprovalDocument;
import erp.approvalrequest.dto.ApprovalCreateRequest;
import erp.approvalrequest.dto.ApprovalResponse;
import erp.approvalrequest.repository.ApprovalRepository;
import erp.common.exception.CustomException;
import erp.common.exception.ErrorCode;
import erp.common.security.AuthUtil;
import erp.common.security.Role;
import erp.common.messaging.ApprovalMessagingConstants;
import erp.shared.proto.approval.ApprovalRequest;
import erp.shared.proto.approval.ApprovalResultStatus;
import erp.shared.proto.approval.Step;
import erp.shared.proto.approval.StepStatus;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ApprovalRequestService {

    private final ApprovalRepository approvalRepository;
    private final RabbitTemplate rabbitTemplate;
    private final AuthUtil authUtil;
    private final RequestIdGenerator requestIdGenerator;
    private final EmployeeClient employeeClient;
    private final NotificationClient notificationClient;

    @Value("${processing.retry.max-attempts}")
    private int processingMaxAttempts;

    @Value("${processing.retry.backoff-millis}")
    private long processingBackoffMillis;

    @Value("${approval.lock.retry.max-attempts}")
    private int lockMaxAttempts;

    @Value("${approval.lock.retry.backoff-millis}")
    private long lockBackoffMillis;

    @Transactional
    public ApprovalResponse create(ApprovalCreateRequest request) {
        Long requesterId = authUtil.currentUserId();
        employeeClient.findById(requesterId); // 요청자 존재 확인

        List<ApprovalCreateRequest.StepDto> steps = request.steps();

        validateSteps(requesterId, steps);

        long requestId = requestIdGenerator.nextId();
        Instant now = Instant.now();
        List<ApprovalDocument.StepInfo> stepInfos = steps.stream()
                .map(s -> ApprovalDocument.StepInfo.builder()
                        .step(s.step())
                        .approverId(s.approverId())
                        .status(StepStatus.STEP_STATUS_PENDING)
                        .build())
                .collect(Collectors.toCollection(ArrayList::new));

        ApprovalDocument document = ApprovalDocument.builder()
                .requestId(requestId)
                .requesterId(requesterId)
                .title(request.title())
                .content(request.content())
                .steps(stepInfos)
                .createdAt(now)
                .updatedAt(now)
                .finalStatus(StepStatus.STEP_STATUS_PENDING)
                .build();

        ApprovalDocument saved = approvalRepository.save(document);
        sendToProcessing(saved);

        return ApprovalResponse.from(saved);
    }

    public List<ApprovalResponse> listForCurrentUser() {
        Long userId = authUtil.currentUserId();
        List<ApprovalDocument> docs;
        if (authUtil.hasRole(Role.ADMIN)) {
            docs = approvalRepository.findAll();
        } else {
            docs = approvalRepository.findByRequesterIdOrStepsApproverId(userId, userId);
        }
        return docs.stream().map(ApprovalResponse::from).toList();
    }

    public ApprovalResponse findOne(Long requestId) {
        ApprovalDocument doc = approvalRepository.findByRequestId(requestId)
                .orElseThrow(() -> new CustomException(ErrorCode.APPROVAL_REQUEST_NOT_FOUND));
        enforceAccess(doc);
        return ApprovalResponse.from(doc);
    }

    @Transactional
    public void resendPending(Long requestId) {
        ApprovalDocument doc = approvalRepository.findByRequestId(requestId)
                .orElseThrow(() -> new CustomException(ErrorCode.APPROVAL_REQUEST_NOT_FOUND));
        enforceAccess(doc);
        sendToProcessing(doc);
    }

    public void updateResult(long requestId, long approverId, int step, ApprovalResultStatus status) {
        for (int attempt = 1; attempt <= Math.max(1, lockMaxAttempts); attempt++) {
            try {
                updateResultInternal(requestId, approverId, step, status);
                return;
            } catch (OptimisticLockingFailureException ex) {
                if (attempt == lockMaxAttempts) {
                    throw new CustomException(ErrorCode.APPROVAL_PROCESS_CONFLICT);
                }
                sleep(lockBackoffMillis);
            }
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    void updateResultInternal(long requestId, long approverId, int step, ApprovalResultStatus status) {
        ApprovalDocument doc = approvalRepository.findByRequestId(requestId)
                .orElseThrow(() -> new CustomException(ErrorCode.APPROVAL_REQUEST_NOT_FOUND));

        ApprovalDocument.StepInfo target = doc.getSteps().stream()
                .filter(s -> s.getStep() == step && s.getApproverId().equals(approverId))
                .findFirst()
                .orElseThrow(() -> new CustomException(ErrorCode.APPROVAL_PROCESS_NOT_FOUND));

        // idempotent: 동일 상태면 그대로 통과, 다른 상태면 예외
        if (target.getStatus() != StepStatus.STEP_STATUS_PENDING) {
            StepStatus incoming = mapStatus(status);
            if (target.getStatus() == incoming) {
                return; // 중복 콜백이지만 상태 일치 → 무해 처리
            }
            throw new CustomException(ErrorCode.APPROVAL_PROCESS_INVALID_STATUS);
        }

        // 현재 처리 가능한 최우선 pending 단계인지 검증하여 idempotency 및 순서 보장
        ApprovalDocument.StepInfo firstPending = doc.getSteps().stream()
                .sorted(Comparator.comparingInt(ApprovalDocument.StepInfo::getStep))
                .filter(s -> s.getStatus() == StepStatus.STEP_STATUS_PENDING)
                .findFirst()
                .orElseThrow(() -> new CustomException(ErrorCode.APPROVAL_PROCESS_INVALID_STATUS));
        if (!firstPending.equals(target)) {
            throw new CustomException(ErrorCode.APPROVAL_PROCESS_INVALID_STATUS);
        }

        target.updateStatus(mapStatus(status));
        doc.setUpdatedAt(Instant.now());

        if (status == ApprovalResultStatus.APPROVAL_RESULT_REJECTED) {
            doc.setFinalStatus(StepStatus.STEP_STATUS_REJECTED);
            approvalRepository.save(doc);
            notifyRequester(doc, "rejected", approverId);
            return;
        }

        boolean allApproved = doc.getSteps().stream()
                .allMatch(s -> s.getStatus() == StepStatus.STEP_STATUS_APPROVED);

        if (allApproved) {
            doc.setFinalStatus(StepStatus.STEP_STATUS_APPROVED);
            approvalRepository.save(doc);
            notifyRequester(doc, "approved", approverId);
            return;
        }

        approvalRepository.save(doc);
        sendToProcessing(doc);
    }

    private void validateSteps(Long requesterId, List<ApprovalCreateRequest.StepDto> steps) {
        if (steps.isEmpty()) {
            throw new CustomException(ErrorCode.APPROVAL_REQUEST_INVALID_STEP);
        }
        int expectedStep = 1;
        for (ApprovalCreateRequest.StepDto step : steps) {
            if (step.step() == null || step.step() != expectedStep) {
                throw new CustomException(ErrorCode.APPROVAL_REQUEST_INVALID_STEP);
            }
            expectedStep++;
            if (step.approverId().equals(requesterId)) {
                throw new CustomException(ErrorCode.APPROVAL_SELF_APPROVAL_NOT_ALLOWED);
            }
            Role role = employeeClient.findRole(step.approverId());
            if (role != Role.APPROVER && role != Role.ADMIN) {
                throw new CustomException(ErrorCode.APPROVAL_APPROVER_NOT_ELIGIBLE);
            }
        }
    }

    private void enforceAccess(ApprovalDocument doc) {
        Long userId = authUtil.currentUserId();
        if (authUtil.hasRole(Role.ADMIN)) {
            return;
        }
        boolean isRequester = doc.getRequesterId().equals(userId);
        boolean isApprover = doc.getSteps().stream().anyMatch(s -> s.getApproverId().equals(userId));
        if (!(isRequester || isApprover)) {
            throw new CustomException(ErrorCode.FORBIDDEN);
        }
    }

    private void sendToProcessing(ApprovalDocument doc) {
        Optional<Step> nextPending = doc.getSteps().stream()
                .sorted(Comparator.comparingInt(ApprovalDocument.StepInfo::getStep))
                .filter(s -> s.getStatus() == StepStatus.STEP_STATUS_PENDING)
                .findFirst()
                .map(s -> Step.newBuilder()
                        .setStep(s.getStep())
                        .setApproverId(s.getApproverId())
                        .setStatus(s.getStatus())
                        .build());

        if (nextPending.isEmpty()) {
            return;
        }

        ApprovalRequest approvalRequestMessage = ApprovalRequest.newBuilder()
                .setRequestId(doc.getRequestId())
                .setRequesterId(doc.getRequesterId())
                .setTitle(doc.getTitle())
                .setContent(doc.getContent())
                .addAllSteps(doc.getSteps().stream()
                        .map(s -> Step.newBuilder()
                                .setStep(s.getStep())
                                .setApproverId(s.getApproverId())
                        .setStatus(s.getStatus())
                        .build())
                        .toList())
                .build();
        callProcessingWithRetry(approvalRequestMessage);
    }

    private StepStatus mapStatus(ApprovalResultStatus status) {
        return switch (status) {
            case APPROVAL_RESULT_APPROVED -> StepStatus.STEP_STATUS_APPROVED;
            case APPROVAL_RESULT_REJECTED -> StepStatus.STEP_STATUS_REJECTED;
            default -> throw new CustomException(ErrorCode.APPROVAL_PROCESS_INVALID_STATUS);
        };
    }

    private void notifyRequester(ApprovalDocument doc, String result, Long approverId) {
        String payload;
        if ("rejected".equals(result)) {
            payload = String.format(
                    "{\"requestId\":%d,\"result\":\"rejected\",\"rejectedBy\":%d,\"finalResult\":\"rejected\"}",
                    doc.getRequestId(),
                    approverId);
        } else {
            payload = String.format(
                    "{\"requestId\":%d,\"result\":\"approved\",\"finalResult\":\"approved\"}",
                    doc.getRequestId());
        }
        notificationClient.send(doc.getRequesterId(), payload);
    }

    private void callProcessingWithRetry(ApprovalRequest request) {
        for (int attempt = 1; attempt <= Math.max(1, processingMaxAttempts); attempt++) {
            try {
                rabbitTemplate.convertAndSend(
                        ApprovalMessagingConstants.EXCHANGE_NAME,
                        ApprovalMessagingConstants.ROUTING_KEY_REQUEST,
                        request.toByteArray());
                return;
            } catch (Exception e) {
                if (attempt == processingMaxAttempts) {
                    throw e;
                }
                sleep(processingBackoffMillis);
            }
        }
    }

    private void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }
    }
}
