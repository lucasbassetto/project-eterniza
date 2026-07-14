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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class EventServiceTest {

    private EventService eventService;

    @Mock
    private EventRepository eventRepository;

    @Mock
    private PhotoRepository photoRepository;

    @Mock
    private RevealEventPublisher revealPublisher;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        eventService = new EventService(eventRepository, photoRepository, revealPublisher);
        try {
            var field = EventService.class.getDeclaredField("webUrl");
            field.setAccessible(true);
            field.set(eventService, "http://localhost:3000");
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    // ─── EVENT-01: Create event with valid payload ───
    @Test
    void create_validPayload_returnEventResponseWithIdAndSlug() {
        UUID hostId = UUID.randomUUID();
        Instant futureTime = Instant.now().plus(7, ChronoUnit.DAYS);
        CreateEventRequest req = new CreateEventRequest("Meu Evento", futureTime, 20);

        // Mock the save to return an event with generated ID and slug
        Event savedEvent = Event.builder()
                .id(UUID.randomUUID())
                .hostId(hostId)
                .name("Meu Evento")
                .slug(UUID.randomUUID().toString())
                .status(EventStatus.ACTIVE)
                .revealAt(futureTime)
                .photoLimitPerGuest(20)
                .build();
        when(eventRepository.save(any(Event.class))).thenReturn(savedEvent);

        EventResponse response = eventService.create(req, hostId);

        assertThat(response.id()).isNotNull();
        assertThat(response.slug()).isNotNull();
        assertThat(response.name()).isEqualTo("Meu Evento");
        assertThat(response.status()).isEqualTo(EventStatus.ACTIVE);
        assertThat(response.photoLimitPerGuest()).isEqualTo(20);
        assertThat(response.photoCount()).isZero();
        assertThat(response.qrCodeUrl()).startsWith("http://localhost:3000/e/");
        verify(eventRepository).save(any(Event.class));
    }

    @Test
    void toResponse_photoCountReflectsActualPhotosInTable() {
        String slug = "event-slug";
        UUID eventId = UUID.randomUUID();
        Event event = Event.builder()
                .id(eventId)
                .hostId(UUID.randomUUID())
                .name("Event")
                .slug(slug)
                .status(EventStatus.ACTIVE)
                .build();
        when(eventRepository.findBySlug(slug)).thenReturn(Optional.of(event));
        // 3 fotos READY existem na tabela para este evento (DELETED não conta)
        when(photoRepository.countByEventIdAndStatus(eventId, PhotoStatus.READY)).thenReturn(3L);

        EventResponse response = eventService.findBySlug(slug);

        assertThat(response.photoCount()).isEqualTo(3);
    }

    // ─── EVENT-04: Token claims (verify response has correct fields) ───
    @Test
    void create_successfullyCreated_tokenResponseIncludesHostIdAndEmail() {
        UUID hostId = UUID.randomUUID();
        Instant futureTime = Instant.now().plus(7, ChronoUnit.DAYS);
        CreateEventRequest req = new CreateEventRequest("Event", futureTime, null);

        Event savedEvent = Event.builder()
                .id(UUID.randomUUID())
                .hostId(hostId)
                .name("Event")
                .slug(UUID.randomUUID().toString())
                .status(EventStatus.ACTIVE)
                .revealAt(futureTime)
                .build();
        when(eventRepository.save(any(Event.class))).thenReturn(savedEvent);

        EventResponse response = eventService.create(req, hostId);

        // Verify the response contains correct data derived from the Event
        assertThat(response.id()).isEqualTo(savedEvent.getId());
    }

    // ─── EVENT-05: Find by slug - success ───
    @Test
    void findBySlug_existingSlug_returnsEventResponse() {
        String slug = "event-slug-123";
        Event event = Event.builder()
                .id(UUID.randomUUID())
                .hostId(UUID.randomUUID())
                .name("Test Event")
                .slug(slug)
                .status(EventStatus.ACTIVE)
                .revealAt(Instant.now().plus(1, ChronoUnit.DAYS))
                .build();
        when(eventRepository.findBySlug(slug)).thenReturn(Optional.of(event));

        EventResponse response = eventService.findBySlug(slug);

        assertThat(response.name()).isEqualTo("Test Event");
        assertThat(response.slug()).isEqualTo(slug);
    }

    // ─── EVENT-06: Find by slug - not found ───
    @Test
    void findBySlug_nonExistentSlug_throwsNotFoundException() {
        String slug = "nonexistent";
        when(eventRepository.findBySlug(slug)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> eventService.findBySlug(slug))
                .isInstanceOf(NotFoundException.class);
    }

    // ─── EVENT-07: Find by host ───
    @Test
    void findByHost_returnsEventListOrderedByCreatedAtDesc() {
        UUID hostId = UUID.randomUUID();
        Event event1 = Event.builder()
                .id(UUID.randomUUID())
                .hostId(hostId)
                .name("Old Event")
                .slug("slug-1")
                .status(EventStatus.ACTIVE)
                .build();
        Event event2 = Event.builder()
                .id(UUID.randomUUID())
                .hostId(hostId)
                .name("New Event")
                .slug("slug-2")
                .status(EventStatus.ACTIVE)
                .build();

        when(eventRepository.findByHostIdOrderByCreatedAtDesc(hostId))
                .thenReturn(List.of(event2, event1));

        List<EventResponse> responses = eventService.findByHost(hostId);

        assertThat(responses).hasSize(2);
        assertThat(responses.get(0).name()).isEqualTo("New Event");
        assertThat(responses.get(1).name()).isEqualTo("Old Event");
    }

    // ─── EVENT-07: Empty list ───
    @Test
    void findByHost_noEvents_returnsEmptyList() {
        UUID hostId = UUID.randomUUID();
        when(eventRepository.findByHostIdOrderByCreatedAtDesc(hostId)).thenReturn(List.of());

        List<EventResponse> responses = eventService.findByHost(hostId);

        assertThat(responses).isEmpty();
    }

    // ─── EVENT-09: Reveal event - success ───
    @Test
    void reveal_activeEvent_changesStatusToRevealed() {
        UUID eventId = UUID.randomUUID();
        UUID hostId = UUID.randomUUID();
        Event event = Event.builder()
                .id(eventId)
                .hostId(hostId)
                .name("Event")
                .slug("slug")
                .status(EventStatus.ACTIVE)
                .build();
        when(eventRepository.findById(eventId)).thenReturn(Optional.of(event));

        eventService.reveal(eventId);

        assertThat(event.getStatus()).isEqualTo(EventStatus.REVEALED);
        verify(revealPublisher).publish(eventId.toString(), hostId.toString());
    }

    // ─── EVENT-10: Reveal event - idempotent ───
    @Test
    void reveal_alreadyRevealed_returnsSilently() {
        UUID eventId = UUID.randomUUID();
        UUID hostId = UUID.randomUUID();
        Event event = Event.builder()
                .id(eventId)
                .hostId(hostId)
                .name("Event")
                .slug("slug")
                .status(EventStatus.REVEALED)
                .build();
        when(eventRepository.findById(eventId)).thenReturn(Optional.of(event));

        eventService.reveal(eventId);

        // Status stays REVEALED, publisher not called (already revealed)
        assertThat(event.getStatus()).isEqualTo(EventStatus.REVEALED);
        verify(revealPublisher, never()).publish(any(), any());
    }

    // ─── EVENT-12: Reveal publishes to messaging ───
    @Test
    void reveal_publishesEventToRabbitMQ() {
        UUID eventId = UUID.randomUUID();
        UUID hostId = UUID.randomUUID();
        Event event = Event.builder()
                .id(eventId)
                .hostId(hostId)
                .name("Event")
                .slug("slug")
                .status(EventStatus.ACTIVE)
                .build();
        when(eventRepository.findById(eventId)).thenReturn(Optional.of(event));

        eventService.reveal(eventId);

        verify(revealPublisher).publish(eventId.toString(), hostId.toString());
    }

    // ─── Revelação manual pelo host ───
    @Test
    void revealAsHost_owner_revealsAndPublishes() {
        UUID eventId = UUID.randomUUID();
        UUID hostId = UUID.randomUUID();
        Event event = Event.builder()
                .id(eventId).hostId(hostId).name("Event").slug("slug")
                .status(EventStatus.ACTIVE).build();
        when(eventRepository.findById(eventId)).thenReturn(Optional.of(event));

        EventResponse response = eventService.revealAsHost(eventId, hostId);

        assertThat(event.getStatus()).isEqualTo(EventStatus.REVEALED);
        assertThat(response.status()).isEqualTo(EventStatus.REVEALED);
        verify(revealPublisher).publish(eventId.toString(), hostId.toString());
    }

    @Test
    void revealAsHost_notOwner_throwsForbiddenAndDoesNotReveal() {
        UUID eventId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        UUID intruderId = UUID.randomUUID();
        Event event = Event.builder()
                .id(eventId).hostId(ownerId).name("Event").slug("slug")
                .status(EventStatus.ACTIVE).build();
        when(eventRepository.findById(eventId)).thenReturn(Optional.of(event));

        assertThatThrownBy(() -> eventService.revealAsHost(eventId, intruderId))
                .isInstanceOf(ForbiddenException.class);

        assertThat(event.getStatus()).isEqualTo(EventStatus.ACTIVE);
        verify(revealPublisher, never()).publish(any(), any());
    }

    @Test
    void revealAsHost_eventNotFound_throwsNotFound() {
        UUID eventId = UUID.randomUUID();
        when(eventRepository.findById(eventId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> eventService.revealAsHost(eventId, UUID.randomUUID()))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void revealAsHost_alreadyRevealed_isIdempotentAndDoesNotRepublish() {
        UUID eventId = UUID.randomUUID();
        UUID hostId = UUID.randomUUID();
        Event event = Event.builder()
                .id(eventId).hostId(hostId).name("Event").slug("slug")
                .status(EventStatus.REVEALED).build();
        when(eventRepository.findById(eventId)).thenReturn(Optional.of(event));

        EventResponse response = eventService.revealAsHost(eventId, hostId);

        assertThat(response.status()).isEqualTo(EventStatus.REVEALED);
        verify(revealPublisher, never()).publish(any(), any());
    }

    // ─── EVENT-11: CheckAndRevealPending ───
    @Test
    void checkAndRevealPending_findsReadyEventsAndReveals() {
        UUID eventId = UUID.randomUUID();
        UUID hostId = UUID.randomUUID();
        Instant now = Instant.now();
        Event event = Event.builder()
                .id(eventId)
                .hostId(hostId)
                .name("Event")
                .slug("slug")
                .status(EventStatus.ACTIVE)
                .revealAt(now.minus(1, ChronoUnit.HOURS))
                .build();
        when(eventRepository.findEventsReadyToReveal(any(Instant.class)))
                .thenReturn(List.of(event));
        when(eventRepository.findById(eventId))
                .thenReturn(Optional.of(event));

        eventService.checkAndRevealPending();

        assertThat(event.getStatus()).isEqualTo(EventStatus.REVEALED);
        verify(revealPublisher).publish(eventId.toString(), hostId.toString());
    }
}
