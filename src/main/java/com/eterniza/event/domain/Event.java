package com.eterniza.event.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "events")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Event {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private UUID hostId;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false, unique = true)
    private String slug;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private EventStatus status = EventStatus.ACTIVE;

    @Column(nullable = false)
    private Instant revealAt;

    @Column(nullable = false)
    @Builder.Default
    private int guestLimit = 5;

    @Column(nullable = false)
    @Builder.Default
    private int guestCount = 0;

    @Column(nullable = false)
    @Builder.Default
    private int photoCount = 0;

    @CreationTimestamp
    private Instant createdAt;

    @UpdateTimestamp
    private Instant updatedAt;

    public boolean isRevealed() {
        return status == EventStatus.REVEALED;
    }

    public boolean isGuestLimitReached() {
        return guestCount >= guestLimit;
    }
}
