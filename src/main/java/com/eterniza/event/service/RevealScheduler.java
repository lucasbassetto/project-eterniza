package com.eterniza.event.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class RevealScheduler {

    private final EventService eventService;

    @Scheduled(fixedDelay = 60_000)
    public void run() {
        log.debug("Verificando eventos para revelar...");
        eventService.checkAndRevealPending();
    }
}
