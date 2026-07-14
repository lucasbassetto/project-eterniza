package com.eterniza.notification.consumer;

import com.eterniza.auth.repository.HostRepository;
import com.eterniza.event.repository.EventRepository;
import com.eterniza.notification.service.EmailService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;

import static com.eterniza.event.messaging.RabbitMQConfig.REVEAL_QUEUE;

@Slf4j
@Component
@RequiredArgsConstructor
public class RevealNotificationConsumer {

    private final EmailService emailService;
    private final HostRepository hostRepository;
    private final EventRepository eventRepository;

    @RabbitListener(queues = REVEAL_QUEUE)
    public void onReveal(Map<String, String> msg) {
        String eventId = msg.get("eventId");
        String hostId  = msg.get("hostId");

        var event = eventRepository.findById(UUID.fromString(eventId)).orElse(null);
        var host  = hostRepository.findById(UUID.fromString(hostId)).orElse(null);

        if (event == null || host == null) {
            log.warn("Evento ou host não encontrado para notificação: eventId={}", eventId);
            return;
        }

        emailService.sendRevealEmail(host.getEmail(), eventId, event.getName());
    }
}
