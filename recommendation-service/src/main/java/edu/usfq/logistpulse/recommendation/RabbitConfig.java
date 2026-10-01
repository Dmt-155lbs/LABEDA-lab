package edu.usfq.logistpulse.recommendation;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitConfig {
    public static final String EXCHANGE = "logistpulse.events";
    public static final String QUEUE = "recommendation.stockout-risk.q";
    public static final String ROUTING_KEY = "inventory.stockout-risk.detected";

    @Bean
    TopicExchange logistpulseExchange() {
        return new TopicExchange(EXCHANGE, true, false);
    }

    @Bean
    Queue stockoutRiskQueue() {
        return new Queue(QUEUE, true);
    }

    @Bean
    Binding stockoutRiskBinding(Queue stockoutRiskQueue, TopicExchange logistpulseExchange) {
        return BindingBuilder.bind(stockoutRiskQueue).to(logistpulseExchange).with(ROUTING_KEY);
    }

    @Bean
    Jackson2JsonMessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    @Bean
    org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory rabbitListenerContainerFactory(
            ConnectionFactory connectionFactory, Jackson2JsonMessageConverter converter) {
        var factory = new org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        factory.setMessageConverter(converter);
        return factory;
    }
}
