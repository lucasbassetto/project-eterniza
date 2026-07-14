package com.eterniza.event.messaging;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.amqp.AmqpConnectException;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

class RevealEventPublisherTest {

    private RevealEventPublisher publisher;

    @Mock private RabbitTemplate rabbitTemplate;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        publisher = new RevealEventPublisher(rabbitTemplate);
    }

    @Test
    void publish_sendsMessageWithEventIdAndHostId() {
        publisher.publish("event-1", "host-1");

        verify(rabbitTemplate).convertAndSend(
                eq(RabbitMQConfig.EXCHANGE),
                eq(RabbitMQConfig.REVEAL_KEY),
                (Object) eq(Map.of("eventId", "event-1", "hostId", "host-1")));
    }

    @Test
    void publish_brokerDown_doesNotThrow() {
        // O publish roda dentro da transação que revela o evento: se a exceção subisse,
        // faria rollback e uma queda do broker impediria a revelação.
        doThrow(new AmqpConnectException(new RuntimeException("broker fora")))
                .when(rabbitTemplate).convertAndSend(any(String.class), any(String.class), (Object) any());

        assertThatCode(() -> publisher.publish("event-1", "host-1"))
                .doesNotThrowAnyException();
    }
}
