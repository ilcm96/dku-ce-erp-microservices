package erp.approvalprocessing.messaging;

import static erp.common.messaging.ApprovalMessagingConstants.EXCHANGE_NAME;
import static erp.common.messaging.ApprovalMessagingConstants.REQUEST_QUEUE_NAME;
import static erp.common.messaging.ApprovalMessagingConstants.RESULT_QUEUE_NAME;
import static erp.common.messaging.ApprovalMessagingConstants.ROUTING_KEY_REQUEST;
import static erp.common.messaging.ApprovalMessagingConstants.ROUTING_KEY_RESULT;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Exchange;
import org.springframework.amqp.core.ExchangeBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.amqp.rabbit.annotation.EnableRabbit;

@Configuration
@EnableRabbit
public class ApprovalProcessingMessagingConfig {

    @Bean
    public Exchange approvalExchange() {
        return ExchangeBuilder.topicExchange(EXCHANGE_NAME).durable(true).build();
    }

    @Bean
    public Queue approvalRequestQueue() {
        return QueueBuilder.durable(REQUEST_QUEUE_NAME).build();
    }

    @Bean
    public Queue approvalResultQueue() {
        return QueueBuilder.durable(RESULT_QUEUE_NAME).build();
    }

    @Bean
    public Binding approvalRequestBinding(Queue approvalRequestQueue, Exchange approvalExchange) {
        return BindingBuilder.bind(approvalRequestQueue)
                .to(approvalExchange)
                .with(ROUTING_KEY_REQUEST)
                .noargs();
    }

    @Bean
    public Binding approvalResultBinding(Queue approvalResultQueue, Exchange approvalExchange) {
        return BindingBuilder.bind(approvalResultQueue)
                .to(approvalExchange)
                .with(ROUTING_KEY_RESULT)
                .noargs();
    }
}
