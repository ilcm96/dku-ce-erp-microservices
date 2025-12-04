package erp.approvalprocessing.grpc;

import erp.shared.proto.approval.ApprovalGrpc;
import erp.shared.proto.approval.ApprovalRequest;
import erp.shared.proto.approval.ApprovalResponse;
import erp.shared.proto.approval.ApprovalResultRequest;
import erp.shared.proto.approval.ApprovalResultResponse;
import io.grpc.stub.StreamObserver;
import org.springframework.grpc.server.service.GrpcService;

import erp.approvalprocessing.service.ApprovalProcessingService;
import lombok.RequiredArgsConstructor;

/**
 * Approval Request Service 와 통신하는 gRPC 엔드포인트.
 * 인입된 요청은 내부 대기열 서비스로 전달하고 즉시 응답을 반환한다.
 */
@GrpcService
@RequiredArgsConstructor
public class ApprovalGrpcService extends ApprovalGrpc.ApprovalImplBase {

    private final ApprovalProcessingService approvalProcessingService;

    @Override
    public void requestApproval(ApprovalRequest request, StreamObserver<ApprovalResponse> responseObserver) {
        approvalProcessingService.acceptRequest(request);
        ApprovalResponse response = ApprovalResponse.newBuilder()
                .setStatus("received")
                .build();

        responseObserver.onNext(response);
        responseObserver.onCompleted();
    }

    @Override
    public void returnApprovalResult(ApprovalResultRequest request, StreamObserver<ApprovalResultResponse> responseObserver) {
        approvalProcessingService.acceptResult(request);
        ApprovalResultResponse response = ApprovalResultResponse.newBuilder()
                .setStatus("accepted")
                .build();

        responseObserver.onNext(response);
        responseObserver.onCompleted();
    }
}
