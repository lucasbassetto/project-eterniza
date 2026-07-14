package com.eterniza.photo.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "photos")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Photo {

    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false) private UUID eventId;
    @Column(nullable = false) private String guestDeviceId;
    @Column(nullable = false) private String guestName;
    @Column(nullable = false) private String originalKey;
    private String filteredKey;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private PhotoStatus status = PhotoStatus.READY;

    @CreationTimestamp private Instant createdAt;
}
