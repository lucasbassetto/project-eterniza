package com.eterniza.event.service;

import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.*;

class RevealSchedulerTest {

    private final EventService eventService = mock(EventService.class);
    private final RevealScheduler scheduler = new RevealScheduler(eventService);

    @Test
    void run_callsEventServiceCheckAndRevealPending() {
        scheduler.run();

        verify(eventService, times(1)).checkAndRevealPending();
    }
}
