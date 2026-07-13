package com.eterniza.event.repository;

import com.eterniza.event.domain.Event;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface EventRepository extends JpaRepository<Event, UUID> {
    Optional<Event> findBySlug(String slug);
    List<Event> findByHostIdOrderByCreatedAtDesc(UUID hostId);

    @Query("SELECT e FROM Event e WHERE e.status = 'ACTIVE' AND e.revealAt <= :now")
    List<Event> findEventsReadyToReveal(Instant now);
}
