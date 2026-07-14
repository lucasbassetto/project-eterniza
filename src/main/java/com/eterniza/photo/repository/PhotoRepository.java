package com.eterniza.photo.repository;

import com.eterniza.photo.domain.Photo;
import com.eterniza.photo.domain.PhotoStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface PhotoRepository extends JpaRepository<Photo, UUID> {
    List<Photo> findByEventIdAndStatus(UUID eventId, PhotoStatus status);
    long countByEventIdAndStatus(UUID eventId, PhotoStatus status);
    // Conta todos os status (inclui DELETED): pose gasta não volta ao apagar.
    long countByEventIdAndGuestDeviceId(UUID eventId, String guestDeviceId);
}
