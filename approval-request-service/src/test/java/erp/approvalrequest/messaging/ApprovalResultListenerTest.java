package erp.approvalrequest.messaging;

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

import erp.approvalrequest.service.ApprovalRequestService;
import erp.common.exception.CustomException;
import erp.common.exception.ErrorCode;
import erp.shared.proto.approval.ApprovalResultRequest;
import erp.shared.proto.approval.ApprovalResultStatus;

@ExtendWith(MockitoExtension.class)
class ApprovalResultListenerTest {

    @Mock
    ApprovalRequestService approvalRequestService;

    @InjectMocks
    ApprovalResultListener listener;

    @Test
    void 결과_메시지를_수신하면_updateResult를_호출한다() throws Exception {
        // given
        ApprovalResultRequest request = ApprovalResultRequest.newBuilder()
                .setRequestId(1L)
                .setApproverId(2L)
                .setStep(1)
                .setStatus(ApprovalResultStatus.APPROVAL_RESULT_APPROVED)
                .build();

        // when
        listener.handleResult(request.toByteArray());

        // then
        verify(approvalRequestService).updateResult(1L, 2L, 1, ApprovalResultStatus.APPROVAL_RESULT_APPROVED);
    }

    @Test
    void 역직렬화_실패하면_메시지를_requeue하지_않고_예외를_던진다() {
        // when & then
        assertThatThrownBy(() -> listener.handleResult("invalid".getBytes()))
                .isInstanceOf(AmqpRejectAndDontRequeueException.class);
        verifyNoInteractions(approvalRequestService);
    }

    @Test
    void 비즈니스_예외는_삼키고_ACK한다() throws Exception {
        // given
        ApprovalResultRequest request = ApprovalResultRequest.newBuilder()
                .setRequestId(1L)
                .setApproverId(2L)
                .setStep(1)
                .setStatus(ApprovalResultStatus.APPROVAL_RESULT_REJECTED)
                .build();

        doThrow(new CustomException(ErrorCode.APPROVAL_PROCESS_INVALID_STATUS))
                .when(approvalRequestService)
                .updateResult(1L, 2L, 1, ApprovalResultStatus.APPROVAL_RESULT_REJECTED);

        // when & then
        assertThatCode(() -> listener.handleResult(request.toByteArray()))
                .doesNotThrowAnyException();
        verify(approvalRequestService).updateResult(1L, 2L, 1, ApprovalResultStatus.APPROVAL_RESULT_REJECTED);
    }
}
