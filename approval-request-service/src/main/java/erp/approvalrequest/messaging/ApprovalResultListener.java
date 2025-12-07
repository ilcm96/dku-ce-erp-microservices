package erp.approvalrequest.messaging;

import static erp.common.messaging.ApprovalMessagingConstants.RESULT_QUEUE_NAME;

import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import com.google.protobuf.InvalidProtocolBufferException;

import erp.approvalrequest.service.ApprovalRequestService;
import erp.common.exception.CustomException;
import erp.shared.proto.approval.ApprovalResultRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Component
@RequiredArgsConstructor
@Slf4j
public class ApprovalResultListener {

    private final ApprovalRequestService approvalRequestService;

    @RabbitListener(queues = RESULT_QUEUE_NAME)
    public void handleResult(byte[] payload) {
        try {
            ApprovalResultRequest request = ApprovalResultRequest.parseFrom(payload);
            approvalRequestService.updateResult(
                    request.getRequestId(), request.getApproverId(), request.getStep(), request.getStatus());
        } catch (InvalidProtocolBufferException e) {
            log.error("결재 결과 메시지 역직렬화에 실패했습니다.", e);
            throw new AmqpRejectAndDontRequeueException("invalid approval result payload", e);
        } catch (CustomException e) {
            log.warn("비즈니스 예외로 결과 메시지를 무시합니다: {}", e.getErrorCode());
        }
    }
}
