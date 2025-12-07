package erp.approvalprocessing.support;

import org.mockito.Mockito;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;

@TestConfiguration
@Profile("test")
public class TestRabbitConfig {

    @Bean
    @Primary
    public RabbitTemplate testRabbitTemplate() {
        return Mockito.mock(RabbitTemplate.class);
    }

    @Bean
    @Primary
    public AmqpAdmin testAmqpAdmin() {
        return Mockito.mock(AmqpAdmin.class);
    }
}
