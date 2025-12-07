package erp.approvalprocessing.messaging;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;

import erp.approvalprocessing.service.ApprovalProcessingService;
import erp.common.exception.CustomException;
import erp.common.exception.ErrorCode;
import erp.shared.proto.approval.ApprovalRequest;
import erp.shared.proto.approval.Step;
import erp.shared.proto.approval.StepStatus;

@ExtendWith(MockitoExtension.class)
class ApprovalRequestListenerTest {

    @Mock
    ApprovalProcessingService approvalProcessingService;

    @InjectMocks
    ApprovalRequestListener listener;

    @Test
    void 요청_메시지를_수신하면_acceptRequest를_호출한다() throws Exception {
        // given
        ApprovalRequest request = ApprovalRequest.newBuilder()
                .setRequestId(1L)
                .setRequesterId(9L)
                .setTitle("title")
                .addSteps(Step.newBuilder()
                        .setStep(1)
                        .setApproverId(2L)
                        .setStatus(StepStatus.STEP_STATUS_PENDING)
                        .build())
                .build();

        // when
        listener.handleRequest(request.toByteArray());

        // then
        verify(approvalProcessingService).acceptRequest(request);
    }

    @Test
    void 역직렬화_실패시_DLQ로_보내도록_예외를_던진다() {
        // when & then
        assertThatThrownBy(() -> listener.handleRequest("bad".getBytes()))
                .isInstanceOf(AmqpRejectAndDontRequeueException.class);
        verifyNoInteractions(approvalProcessingService);
    }

    @Test
    void 비즈니스_예외는_삼키고_ACK한다() throws Exception {
        // given
        ApprovalRequest request = ApprovalRequest.newBuilder().build();
        doThrow(new CustomException(ErrorCode.APPROVAL_PROCESS_NOT_FOUND))
                .when(approvalProcessingService)
                .acceptRequest(request);

        // when & then
        assertThatCode(() -> listener.handleRequest(request.toByteArray()))
                .doesNotThrowAnyException();
        verify(approvalProcessingService).acceptRequest(request);
    }
}
