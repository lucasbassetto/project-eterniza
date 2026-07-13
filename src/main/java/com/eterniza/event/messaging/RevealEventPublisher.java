package com.eterniza.event.messaging;

import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
@RequiredArgsConstructor
public class RevealEventPublisher {

    private final RabbitTemplate rabbitTemplate;

    public void publish(String eventId, String hostId) {
        rabbitTemplate.convertAndSend(
                RabbitMQConfig.EXCHANGE,
                RabbitMQConfig.REVEAL_KEY,
                Map.of("eventId", eventId, "hostId", hostId)
        );
    }
}
