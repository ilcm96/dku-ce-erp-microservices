package erp.approvalrequest.grpc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import erp.approvalrequest.service.ApprovalRequestService;
import erp.shared.proto.approval.ApprovalRequest;
import erp.shared.proto.approval.ApprovalResponse;
import erp.shared.proto.approval.ApprovalResultRequest;
import erp.shared.proto.approval.ApprovalResultResponse;
import erp.shared.proto.approval.ApprovalResultStatus;
import io.grpc.stub.StreamObserver;

@ExtendWith(MockitoExtension.class)
class ApprovalGrpcServerTest {

    @Mock
    private ApprovalRequestService approvalRequestService;

    @InjectMocks
    private ApprovalGrpcServer approvalGrpcServer;

    @Test
    void returnApprovalResult_호출시_서비스로_위임하고_accepted를_반환한다() {
        // given
        ApprovalResultRequest request = ApprovalResultRequest.newBuilder()
                .setRequestId(1L)
                .setApproverId(2L)
                .setStep(1)
                .setStatus(ApprovalResultStatus.APPROVAL_RESULT_APPROVED)
                .build();
        TestObserver<ApprovalResultResponse> observer = new TestObserver<>();

        // when
        approvalGrpcServer.returnApprovalResult(request, observer);

        // then: 서비스 위임 검증
        verify(approvalRequestService).updateResult(1L, 2L, 1, ApprovalResultStatus.APPROVAL_RESULT_APPROVED);
        assertThat(observer.value().getStatus()).isEqualTo("accepted");
    }

    @Test
    void requestApproval는_noop_응답을_반환한다() {
        // given
        ApprovalRequest request = ApprovalRequest.newBuilder().setRequestId(1L).build();
        TestObserver<ApprovalResponse> observer = new TestObserver<>();

        // when
        approvalGrpcServer.requestApproval(request, observer);

        // then
        assertThat(observer.value().getStatus()).isEqualTo("noop");
    }

    private static class TestObserver<T> implements StreamObserver<T> {
        private T value;
        private Throwable error;

        @Override
        public void onNext(T value) {
            this.value = value;
        }

        @Override
        public void onError(Throwable t) {
            this.error = t;
        }

        @Override
        public void onCompleted() {}

        public T value() {
            if (error != null) {
                throw new RuntimeException(error);
            }
            return value;
        }
    }
}
