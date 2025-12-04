package erp.approvalprocessing.dto;

import java.util.List;

import erp.shared.proto.approval.ApprovalRequest;
import erp.shared.proto.approval.Step;
import erp.shared.proto.approval.StepStatus;

/**
 * 승인자 큐 조회 시 REST 응답으로 사용되는 DTO.
 * 내부 큐는 protobuf 메시지를 보관하지만, REST 계층에서는 Jackson 직렬화 가능한 DTO로 변환한다.
 */
public record ApprovalQueueItemResponse(
        Long requestId,
        Long requesterId,
        String title,
        String content,
        List<StepResponse> steps) {

    public static ApprovalQueueItemResponse from(ApprovalRequest proto) {
        return new ApprovalQueueItemResponse(
                proto.getRequestId(),
                proto.getRequesterId(),
                proto.getTitle(),
                proto.getContent(),
                proto.getStepsList().stream()
                        .map(StepResponse::from)
                        .toList());
    }

    public static List<ApprovalQueueItemResponse> fromList(List<ApprovalRequest> protos) {
        return protos.stream().map(ApprovalQueueItemResponse::from).toList();
    }

    public record StepResponse(int step, Long approverId, StepStatus status) {

        public static StepResponse from(Step proto) {
            return new StepResponse(proto.getStep(), proto.getApproverId(), proto.getStatus());
        }
    }
}
