package com.eterniza.event.messaging;

import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class RevealEventPublisherTest {

    private final RabbitTemplate rabbitTemplate = mock(RabbitTemplate.class);
    private final RevealEventPublisher publisher = new RevealEventPublisher(rabbitTemplate);

    @Test
    void publish_callsRabbitTemplate() {
        String eventId = "event-123";
        String hostId = "host-456";

        publisher.publish(eventId, hostId);

        verify(rabbitTemplate, times(1)).convertAndSend(
                eq(RabbitMQConfig.EXCHANGE),
                eq(RabbitMQConfig.REVEAL_KEY),
                any()
        );
    }

    @Test
    void publish_mapsEventIdAndHostId() {
        String eventId = "event-xyz";
        String hostId = "host-abc";

        publisher.publish(eventId, hostId);

        verify(rabbitTemplate).convertAndSend(
                eq(RabbitMQConfig.EXCHANGE),
                eq(RabbitMQConfig.REVEAL_KEY),
                any()
        );
    }
}
