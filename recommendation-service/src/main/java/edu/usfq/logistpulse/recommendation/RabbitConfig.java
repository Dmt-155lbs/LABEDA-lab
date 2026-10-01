package edu.usfq.logistpulse.recommendation;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.config.RetryInterceptorBuilder;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.retry.RejectAndDontRequeueRecoverer;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.retry.backoff.FixedBackOffPolicy;

@Configuration
public class RabbitConfig {
    public static final String EXCHANGE = "logistpulse.events";
    public static final String QUEUE = "recommendation.stockout-risk.q";
    public static final String ROUTING_KEY = "inventory.stockout-risk.detected";

    // Dead-letter: donde termina el evento al agotar los intentos.
    public static final String DLX = "logistpulse.dlx";
    public static final String DLQ = "recommendation.stockout-risk.dlq";
    public static final String DLQ_ROUTING_KEY = "recommendation.stockout-risk.dead";

    // Politica de reintento (intentos totales = original + reintentos).
    public static final int MAX_ATTEMPTS = 3;
    public static final long BACKOFF_MS = 1000L;

    @Bean
    TopicExchange logistpulseExchange() {
        return new TopicExchange(EXCHANGE, true, false);
    }

    @Bean
    DirectExchange deadLetterExchange() {
        return new DirectExchange(DLX, true, false);
    }

    @Bean
    Queue deadLetterQueue() {
        return QueueBuilder.durable(DLQ).build();
    }

    @Bean
    Binding deadLetterBinding(Queue deadLetterQueue, DirectExchange deadLetterExchange) {
        return BindingBuilder.bind(deadLetterQueue).to(deadLetterExchange).with(DLQ_ROUTING_KEY);
    }

    @Bean
    Queue stockoutRiskQueue() {
        // Argumentos de DLX: cambiar esto exige borrar la cola existente (RabbitMQ no permite
        // modificar argumentos de una cola ya declarada: PRECONDITION_FAILED).
        return QueueBuilder.durable(QUEUE)
                .withArgument("x-dead-letter-exchange", DLX)
                .withArgument("x-dead-letter-routing-key", DLQ_ROUTING_KEY)
                .build();
    }

    @Bean
    Binding stockoutRiskBinding(Queue stockoutRiskQueue, TopicExchange logistpulseExchange) {
        return BindingBuilder.bind(stockoutRiskQueue).to(logistpulseExchange).with(ROUTING_KEY);
    }

    @Bean
    Jackson2JsonMessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    private static FixedBackOffPolicy fixedBackOff() {
        var policy = new FixedBackOffPolicy();
        policy.setBackOffPeriod(BACKOFF_MS);
        return policy;
    }

    @Bean
    SimpleRabbitListenerContainerFactory rabbitListenerContainerFactory(
            ConnectionFactory connectionFactory, Jackson2JsonMessageConverter converter) {
        var factory = new SimpleRabbitListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        factory.setMessageConverter(converter);
        // Al definir este factory a mano, las propiedades spring.rabbitmq.listener.simple.retry.*
        // NO se aplican; el reintento se configura explicitamente aqui.
        // RejectAndDontRequeueRecoverer: agotados los intentos, el mensaje se rechaza sin reencolar
        // y RabbitMQ lo enruta al dead-letter exchange de la cola.
        factory.setAdviceChain(RetryInterceptorBuilder.stateless()
                .maxAttempts(MAX_ATTEMPTS)
                .backOffPolicy(fixedBackOff())
                .recoverer(new RejectAndDontRequeueRecoverer())
                .build());
        return factory;
    }
}
