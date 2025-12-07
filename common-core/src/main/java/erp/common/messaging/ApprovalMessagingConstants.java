package erp.common.messaging;

/**
 * RabbitMQ 토폴로지에 사용되는 Exchange/Queue/RoutingKey 상수 모음.
 */
public final class ApprovalMessagingConstants {

    public static final String EXCHANGE_NAME = "approval.exchange";
    public static final String REQUEST_QUEUE_NAME = "approval.request.queue";
    public static final String RESULT_QUEUE_NAME = "approval.result.queue";
    public static final String ROUTING_KEY_REQUEST = "approval.request";
    public static final String ROUTING_KEY_RESULT = "approval.result";

    private ApprovalMessagingConstants() {
    }
}
