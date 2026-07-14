package com.eterniza.event.messaging;

import org.springframework.amqp.core.*;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMQConfig {

    public static final String EXCHANGE         = "eterniza.events";
    public static final String REVEAL_QUEUE     = "eterniza.event.revealed";
    public static final String REVEAL_KEY       = "event.revealed";

    @Bean TopicExchange eventsExchange() {
        return new TopicExchange(EXCHANGE, true, false);
    }

    @Bean Queue revealQueue() { return QueueBuilder.durable(REVEAL_QUEUE).build(); }

    @Bean Binding revealBinding(Queue revealQueue, TopicExchange eventsExchange) {
        return BindingBuilder.bind(revealQueue).to(eventsExchange).with(REVEAL_KEY);
    }

    @Bean Jackson2JsonMessageConverter messageConverter() {
        return new Jackson2JsonMessageConverter();
    }
}
