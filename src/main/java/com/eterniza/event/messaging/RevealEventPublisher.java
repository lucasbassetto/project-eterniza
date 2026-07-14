package com.eterniza.event.messaging;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class RevealEventPublisher {

    private final RabbitTemplate rabbitTemplate;

    /**
     * Publica a notificação de revelação (fire-and-forget).
     *
     * <p>Uma falha do broker é registrada mas <b>não propaga</b>: este publish roda dentro
     * da transação que revela o evento, e deixar a exceção subir faria rollback — ou seja,
     * uma queda do RabbitMQ impediria a revelação. A notificação é efeito colateral; abrir
     * a galeria é a ação essencial e não pode depender do broker.
     */
    public void publish(String eventId, String hostId) {
        try {
            rabbitTemplate.convertAndSend(
                    RabbitMQConfig.EXCHANGE,
                    RabbitMQConfig.REVEAL_KEY,
                    Map.of("eventId", eventId, "hostId", hostId)
            );
        } catch (Exception e) {
            log.error("Falha ao publicar notificação de revelação do evento {} — "
                    + "o evento foi revelado mesmo assim, mas o host não será notificado", eventId, e);
        }
    }
}
