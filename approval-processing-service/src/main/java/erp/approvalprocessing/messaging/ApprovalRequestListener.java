package erp.approvalprocessing.messaging;

import static erp.common.messaging.ApprovalMessagingConstants.REQUEST_QUEUE_NAME;

import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import com.google.protobuf.InvalidProtocolBufferException;

import erp.approvalprocessing.service.ApprovalProcessingService;
import erp.common.exception.CustomException;
import erp.shared.proto.approval.ApprovalRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Component
@RequiredArgsConstructor
@Slf4j
public class ApprovalRequestListener {

    private final ApprovalProcessingService approvalProcessingService;

    @RabbitListener(queues = REQUEST_QUEUE_NAME)
    public void handleRequest(byte[] payload) {
        try {
            ApprovalRequest request = ApprovalRequest.parseFrom(payload);
            approvalProcessingService.acceptRequest(request);
        } catch (InvalidProtocolBufferException e) {
            log.error("결재 요청 메시지 역직렬화에 실패했습니다.", e);
            throw new AmqpRejectAndDontRequeueException("invalid approval request payload", e);
        } catch (CustomException e) {
            log.warn("비즈니스 예외로 요청 메시지를 무시합니다: {}", e.getErrorCode());
        }
    }
}
