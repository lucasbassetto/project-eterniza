package com.eterniza.event.service;

import com.eterniza.common.exception.ForbiddenException;
import com.eterniza.common.exception.NotFoundException;
import com.eterniza.event.domain.Event;
import com.eterniza.event.domain.EventStatus;
import com.eterniza.event.dto.CreateEventRequest;
import com.eterniza.event.dto.EventResponse;
import com.eterniza.event.messaging.RevealEventPublisher;
import com.eterniza.event.repository.EventRepository;
import com.eterniza.photo.domain.PhotoStatus;
import com.eterniza.photo.repository.PhotoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class EventService {

    private final EventRepository eventRepository;
    private final PhotoRepository photoRepository;
    private final RevealEventPublisher revealPublisher;

    @Value("${eterniza.app.web-url}")
    private String webUrl;

    @Transactional
    public EventResponse create(CreateEventRequest req, UUID hostId) {
        Event event = eventRepository.save(Event.builder()
                .hostId(hostId)
                .name(req.name())
                .slug(uniqueSlugFor(req.name()))
                .revealAt(req.revealAt())
                .photoLimitPerGuest(req.photoLimitPerGuest() != null ? req.photoLimitPerGuest() : 10)
                .build());
        return toResponse(event);
    }

    /**
     * Slug legível para o link/QR ("casamento-ana-joao-x7k2"). O sufixo
     * aleatório torna colisão improvável; ainda assim tenta algumas vezes e a
     * constraint UNIQUE do banco segura corrida entre requisições simultâneas.
     */
    private String uniqueSlugFor(String name) {
        for (int attempt = 0; attempt < 5; attempt++) {
            String slug = SlugGenerator.generate(name);
            if (!eventRepository.existsBySlug(slug)) {
                return slug;
            }
        }
        // 5 colisões seguidas: praticamente impossível — cai no UUID, sempre único
        return UUID.randomUUID().toString();
    }

    public EventResponse findBySlug(String slug) {
        return toResponse(eventRepository.findBySlug(slug)
                .orElseThrow(() -> new NotFoundException("Evento", slug)));
    }

    public List<EventResponse> findByHost(UUID hostId) {
        return eventRepository.findByHostIdOrderByCreatedAtDesc(hostId)
                .stream().map(this::toResponse).toList();
    }

    @Transactional
    public void reveal(UUID eventId) {
        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new NotFoundException("Evento", eventId));
        if (event.isRevealed()) return;
        event.setStatus(EventStatus.REVEALED);
        revealPublisher.publish(event.getId().toString(), event.getHostId().toString());
    }

    /**
     * Revelação manual pelo host, antes da revealAt. Só o dono do evento pode revelar.
     * Idempotente: revelar um evento já revelado não republica a notificação.
     */
    @Transactional
    public EventResponse revealAsHost(UUID eventId, UUID hostId) {
        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new NotFoundException("Evento", eventId));

        if (!event.getHostId().equals(hostId)) {
            throw new ForbiddenException("Você não é o dono deste evento");
        }

        if (!event.isRevealed()) {
            event.setStatus(EventStatus.REVEALED);
            revealPublisher.publish(event.getId().toString(), event.getHostId().toString());
        }
        return toResponse(event);
    }

    public void checkAndRevealPending() {
        eventRepository.findEventsReadyToReveal(Instant.now())
                .forEach(e -> reveal(e.getId()));
    }

    private EventResponse toResponse(Event e) {
        // Contagem real vinda da tabela de fotos (fonte da verdade), não de um
        // contador denormalizado no evento — não deriva ao criar/remover fotos.
        // Só READY: fotos moderadas (DELETED) não aparecem na contagem pública.
        int photoCount = (int) photoRepository.countByEventIdAndStatus(e.getId(), PhotoStatus.READY);
        return new EventResponse(e.getId(), e.getName(), e.getSlug(),
                "%s/e/%s".formatted(webUrl, e.getSlug()),
                e.getStatus(), e.getRevealAt(),
                e.getPhotoLimitPerGuest(), photoCount, e.getCreatedAt());
    }
}
