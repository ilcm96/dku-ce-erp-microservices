package erp.approvalrequest.grpc;

import org.springframework.grpc.server.service.GrpcService;

import erp.approvalrequest.service.ApprovalRequestService;
import erp.shared.proto.approval.ApprovalGrpc;
import erp.shared.proto.approval.ApprovalRequest;
import erp.shared.proto.approval.ApprovalResponse;
import erp.shared.proto.approval.ApprovalResultRequest;
import erp.shared.proto.approval.ApprovalResultResponse;
import io.grpc.stub.StreamObserver;
import lombok.RequiredArgsConstructor;

@GrpcService
@RequiredArgsConstructor
public class ApprovalGrpcServer extends ApprovalGrpc.ApprovalImplBase {

    private final ApprovalRequestService approvalRequestService;

    @Override
    public void returnApprovalResult(
            ApprovalResultRequest request, StreamObserver<ApprovalResultResponse> responseObserver) {
        approvalRequestService.updateResult(
                request.getRequestId(), request.getApproverId(), request.getStep(), request.getStatus());

        responseObserver.onNext(ApprovalResultResponse.newBuilder().setStatus("accepted").build());
        responseObserver.onCompleted();
    }

    @Override
    public void requestApproval(ApprovalRequest request, StreamObserver<ApprovalResponse> responseObserver) {
        responseObserver.onNext(ApprovalResponse.newBuilder().setStatus("noop").build());
        responseObserver.onCompleted();
    }
}
