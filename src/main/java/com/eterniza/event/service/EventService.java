package com.eterniza.event.service;

import com.eterniza.common.exception.BusinessException;
import com.eterniza.common.exception.NotFoundException;
import com.eterniza.event.domain.Event;
import com.eterniza.event.domain.EventStatus;
import com.eterniza.event.dto.CreateEventRequest;
import com.eterniza.event.dto.EventResponse;
import com.eterniza.event.messaging.RevealEventPublisher;
import com.eterniza.event.repository.EventRepository;
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
    private final RevealEventPublisher revealPublisher;

    @Value("${eterniza.app.web-url}")
    private String webUrl;

    public EventService(EventRepository eventRepository, RevealEventPublisher revealPublisher, String webUrl) {
        this.eventRepository = eventRepository;
        this.revealPublisher = revealPublisher;
        this.webUrl = webUrl;
    }

    @Transactional
    public EventResponse create(CreateEventRequest req, UUID hostId) {
        Event event = eventRepository.save(Event.builder()
                .hostId(hostId)
                .name(req.name())
                .slug(UUID.randomUUID().toString())
                .filmStyle(req.filmStyle())
                .revealAt(req.revealAt())
                .guestLimit(req.guestLimit() != null ? req.guestLimit() : 5)
                .build());
        return toResponse(event);
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
    public void incrementGuestCount(String slug) {
        Event event = eventRepository.findBySlug(slug)
                .orElseThrow(() -> new NotFoundException("Evento", slug));
        if (event.isGuestLimitReached()) {
            throw new BusinessException("Limite de convidados atingido");
        }
        event.setGuestCount(event.getGuestCount() + 1);
    }

    @Transactional
    public void reveal(UUID eventId) {
        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new NotFoundException("Evento", eventId));
        if (event.isRevealed()) return;
        event.setStatus(EventStatus.REVEALED);
        revealPublisher.publish(event.getId().toString(), event.getHostId().toString());
    }

    public void checkAndRevealPending() {
        eventRepository.findEventsReadyToReveal(Instant.now())
                .forEach(e -> reveal(e.getId()));
    }

    private EventResponse toResponse(Event e) {
        return new EventResponse(e.getId(), e.getName(), e.getSlug(),
                "%s/e/%s".formatted(webUrl, e.getSlug()),
                e.getFilmStyle(), e.getStatus(), e.getRevealAt(),
                e.getGuestLimit(), e.getGuestCount(), e.getPhotoCount(), e.getCreatedAt());
    }
}
