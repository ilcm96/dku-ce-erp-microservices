package erp.approvalrequest.dto;

import java.time.Instant;
import java.util.List;

import erp.approvalrequest.domain.ApprovalDocument;
import erp.shared.proto.approval.StepStatus;

public record ApprovalResponse(
        Long requestId,
        String id,
        Long requesterId,
        String title,
        String content,
        List<StepResponse> steps,
        StepStatus finalStatus,
        Instant createdAt,
        Instant updatedAt) {

    public static ApprovalResponse from(ApprovalDocument doc) {
        return new ApprovalResponse(
                doc.getRequestId(),
                doc.getId(),
                doc.getRequesterId(),
                doc.getTitle(),
                doc.getContent(),
                doc.getSteps().stream()
                        .map(s -> new StepResponse(s.getStep(), s.getApproverId(), s.getStatus()))
                        .toList(),
                doc.getFinalStatus(),
                doc.getCreatedAt(),
                doc.getUpdatedAt());
    }

    public record StepResponse(int step, Long approverId, StepStatus status) {}
}
