package erp.approvalprocessing.grpc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import erp.approvalprocessing.service.ApprovalProcessingService;
import erp.shared.proto.approval.ApprovalRequest;
import erp.shared.proto.approval.ApprovalResponse;
import erp.shared.proto.approval.ApprovalResultRequest;
import erp.shared.proto.approval.ApprovalResultResponse;
import io.grpc.stub.StreamObserver;

@DisplayName("ApprovalGrpcService 단위 테스트")
class ApprovalGrpcServiceTest {

    private final ApprovalProcessingService approvalProcessingService = mock(ApprovalProcessingService.class);
    private final ApprovalGrpcService approvalGrpcService = new ApprovalGrpcService(approvalProcessingService);

    @Test
    @DisplayName("requestApproval 은 요청을 큐에 넣고 status=received 를 반환한다")
    void requestApprovalEnqueuesAndResponds() {
        // given
        ApprovalRequest request = ApprovalRequest.newBuilder()
                .setRequestId(1L)
                .build();
        TestObserver<ApprovalResponse> observer = new TestObserver<>();

        // when
        approvalGrpcService.requestApproval(request, observer);

        // then
        verify(approvalProcessingService).acceptRequest(request);
        assertThat(observer.value.getStatus()).isEqualTo("received");
        assertThat(observer.completed).isTrue();
    }

    @Test
    @DisplayName("returnApprovalResult 는 큐에서 제거하고 status=accepted 를 반환한다")
    void returnApprovalResultRemovesAndResponds() {
        // given
        ApprovalResultRequest request = ApprovalResultRequest.newBuilder()
                .setRequestId(3L)
                .setApproverId(5L)
                .build();
        TestObserver<ApprovalResultResponse> observer = new TestObserver<>();

        // when
        approvalGrpcService.returnApprovalResult(request, observer);

        // then
        verify(approvalProcessingService).acceptResult(request);
        assertThat(observer.value.getStatus()).isEqualTo("accepted");
        assertThat(observer.completed).isTrue();
    }

    private static class TestObserver<T> implements StreamObserver<T> {
        private T value;
        private boolean completed;

        @Override
        public void onNext(T value) {
            this.value = value;
        }

        @Override
        public void onError(Throwable throwable) {
            // 테스트에서는 사용하지 않는다.
        }

        @Override
        public void onCompleted() {
            this.completed = true;
        }
    }
}
