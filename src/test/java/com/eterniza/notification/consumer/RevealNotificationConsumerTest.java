package com.eterniza.notification.consumer;

import com.eterniza.auth.domain.Host;
import com.eterniza.auth.repository.HostRepository;
import com.eterniza.event.domain.Event;
import com.eterniza.event.repository.EventRepository;
import com.eterniza.notification.service.EmailService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.Mockito.*;

class RevealNotificationConsumerTest {

    private RevealNotificationConsumer consumer;

    @Mock private EmailService emailService;
    @Mock private HostRepository hostRepository;
    @Mock private EventRepository eventRepository;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        consumer = new RevealNotificationConsumer(emailService, hostRepository, eventRepository);
    }

    @Test
    void onReveal_validEventAndHost_sendsMail() {
        UUID eventId = UUID.randomUUID();
        UUID hostId = UUID.randomUUID();
        String hostEmail = "host@example.com";
        String eventName = "Festa";

        Event event = Event.builder().id(eventId).name(eventName).build();
        Host host = Host.builder().id(hostId).email(hostEmail).build();

        when(eventRepository.findById(eventId)).thenReturn(Optional.of(event));
        when(hostRepository.findById(hostId)).thenReturn(Optional.of(host));

        consumer.onReveal(Map.of(
                "eventId", eventId.toString(),
                "hostId", hostId.toString()
        ));

        verify(emailService).sendRevealEmail(hostEmail, eventId.toString(), eventName);
    }

    @Test
    void onReveal_eventNotFound_doesNotSendMail() {
        UUID eventId = UUID.randomUUID();
        UUID hostId = UUID.randomUUID();

        when(eventRepository.findById(eventId)).thenReturn(Optional.empty());

        consumer.onReveal(Map.of(
                "eventId", eventId.toString(),
                "hostId", hostId.toString()
        ));

        verify(emailService, never()).sendRevealEmail(anyString(), anyString(), anyString());
    }

    @Test
    void onReveal_hostNotFound_doesNotSendMail() {
        UUID eventId = UUID.randomUUID();
        UUID hostId = UUID.randomUUID();

        Event event = Event.builder().id(eventId).name("Festa").build();
        when(eventRepository.findById(eventId)).thenReturn(Optional.of(event));
        when(hostRepository.findById(hostId)).thenReturn(Optional.empty());

        consumer.onReveal(Map.of(
                "eventId", eventId.toString(),
                "hostId", hostId.toString()
        ));

        verify(emailService, never()).sendRevealEmail(anyString(), anyString(), anyString());
    }
}
