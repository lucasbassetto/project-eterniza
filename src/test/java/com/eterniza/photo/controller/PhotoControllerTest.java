package com.eterniza.photo.controller;

import com.eterniza.common.security.JwtUtil;
import com.eterniza.event.domain.Event;
import com.eterniza.event.domain.EventStatus;
import com.eterniza.event.repository.EventRepository;
import com.eterniza.photo.domain.Photo;
import com.eterniza.photo.domain.PhotoStatus;
import com.eterniza.photo.repository.PhotoRepository;
import com.eterniza.photo.service.StorageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class PhotoControllerTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("eterniza")
            .withUsername("eterniza")
            .withPassword("eterniza123");

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired private MockMvc mockMvc;
    @Autowired private EventRepository eventRepository;
    @Autowired private PhotoRepository photoRepository;
    @Autowired private JwtUtil jwtUtil;

    // External I/O isolated — real controller/service/DB, mocked R2 storage
    @MockBean private StorageService storageService;

    private UUID eventId;
    private String guestToken;

    @BeforeEach
    void setUp() {
        photoRepository.deleteAll();
        eventRepository.deleteAll();

        Event event = eventRepository.save(Event.builder()
                .hostId(UUID.randomUUID())
                .name("Festa")
                .slug("festa-" + UUID.randomUUID().toString().substring(0, 8))
                .status(EventStatus.ACTIVE)
                .revealAt(Instant.now().plus(1, ChronoUnit.DAYS))
                .build());
        eventId = event.getId();
        guestToken = jwtUtil.generateGuestToken("device-abc", "Ana", eventId.toString());
    }

    private MockMultipartFile jpeg(byte[] bytes) {
        return new MockMultipartFile("file", "photo.jpg", "image/jpeg", bytes);
    }

    // ─── IT-PHOTO-01: Upload happy path ───
    @Test
    void upload_validMultipartWithGuestToken_returns202AndPersistsPhoto() throws Exception {
        mockMvc.perform(multipart("/api/photos/upload")
                        .file(jpeg(new byte[]{1, 2, 3, 4}))
                        .param("eventId", eventId.toString())
                        .header("Authorization", "Bearer " + guestToken))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.photoId").exists())
                .andExpect(jsonPath("$.data.message").exists());

        List<Photo> photos = photoRepository.findAll();
        assertThat(photos).hasSize(1);
        Photo saved = photos.get(0);
        assertThat(saved.getStatus()).isEqualTo(PhotoStatus.READY);
        assertThat(saved.getEventId()).isEqualTo(eventId);
        assertThat(saved.getGuestDeviceId()).isEqualTo("device-abc");
        assertThat(saved.getGuestName()).isEqualTo("Ana");
        assertThat(saved.getOriginalKey())
                .startsWith("events/" + eventId + "/originals/")
                .endsWith(".jpg");

        verify(storageService).upload(startsWith("events/" + eventId + "/originals/"), any());
    }

    // ─── IT-PHOTO-02: Upload without auth ───
    @Test
    void upload_withoutAuthorizationHeader_returns401AndPersistsNothing() throws Exception {
        mockMvc.perform(multipart("/api/photos/upload")
                        .file(jpeg(new byte[]{1, 2, 3, 4}))
                        .param("eventId", eventId.toString()))
                .andExpect(status().isUnauthorized());

        assertThat(photoRepository.findAll()).isEmpty();
    }

    // ─── IT-PHOTO-03: Validation errors through the real stack ───
    @Test
    void upload_emptyFile_returns400WithMessage() throws Exception {
        mockMvc.perform(multipart("/api/photos/upload")
                        .file(jpeg(new byte[0]))
                        .param("eventId", eventId.toString())
                        .header("Authorization", "Bearer " + guestToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Arquivo vazio"));

        assertThat(photoRepository.findAll()).isEmpty();
    }

    @Test
    void upload_invalidContentType_returns400WithMessage() throws Exception {
        MockMultipartFile pdf = new MockMultipartFile("file", "doc.pdf", "application/pdf", new byte[]{1, 2});

        mockMvc.perform(multipart("/api/photos/upload")
                        .file(pdf)
                        .param("eventId", eventId.toString())
                        .header("Authorization", "Bearer " + guestToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Formato inválido. Envie JPEG, PNG ou WebP"));

        assertThat(photoRepository.findAll()).isEmpty();
    }

    // ─── Limite de fotos por convidado (câmera descartável) ───
    @Test
    void upload_guestAtPhotoLimit_returns400AndPersistsNothing() throws Exception {
        Event limited = eventRepository.save(Event.builder()
                .hostId(UUID.randomUUID())
                .name("Festa com 1 pose")
                .slug("limitada-" + UUID.randomUUID().toString().substring(0, 8))
                .status(EventStatus.ACTIVE)
                .revealAt(Instant.now().plus(1, ChronoUnit.DAYS))
                .photoLimitPerGuest(1)
                .build());
        String token = jwtUtil.generateGuestToken("device-abc", "Ana", limited.getId().toString());

        // 1ª foto: dentro do limite
        mockMvc.perform(multipart("/api/photos/upload")
                        .file(jpeg(new byte[]{1, 2, 3, 4}))
                        .param("eventId", limited.getId().toString())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isCreated());

        // 2ª foto do mesmo deviceId: limite atingido
        mockMvc.perform(multipart("/api/photos/upload")
                        .file(jpeg(new byte[]{5, 6, 7, 8}))
                        .param("eventId", limited.getId().toString())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Você já usou todas as suas 1 fotos neste evento"));

        assertThat(photoRepository.countByEventIdAndGuestDeviceId(limited.getId(), "device-abc"))
                .isEqualTo(1);
    }

    @Test
    void upload_otherDeviceStillHasItsOwnLimit() throws Exception {
        Event limited = eventRepository.save(Event.builder()
                .hostId(UUID.randomUUID())
                .name("Festa com 1 pose")
                .slug("limitada2-" + UUID.randomUUID().toString().substring(0, 8))
                .status(EventStatus.ACTIVE)
                .revealAt(Instant.now().plus(1, ChronoUnit.DAYS))
                .photoLimitPerGuest(1)
                .build());
        String tokenAna  = jwtUtil.generateGuestToken("device-ana", "Ana", limited.getId().toString());
        String tokenBeto = jwtUtil.generateGuestToken("device-beto", "Beto", limited.getId().toString());

        mockMvc.perform(multipart("/api/photos/upload")
                        .file(jpeg(new byte[]{1, 2}))
                        .param("eventId", limited.getId().toString())
                        .header("Authorization", "Bearer " + tokenAna))
                .andExpect(status().isCreated());

        // O limite é por convidado, não por evento: Beto ainda pode enviar
        mockMvc.perform(multipart("/api/photos/upload")
                        .file(jpeg(new byte[]{3, 4}))
                        .param("eventId", limited.getId().toString())
                        .header("Authorization", "Bearer " + tokenBeto))
                .andExpect(status().isCreated());
    }

    @Test
    void upload_nonExistentEvent_returns404() throws Exception {
        UUID ghostEventId = UUID.randomUUID();
        String token = jwtUtil.generateGuestToken("device-abc", "Ana", ghostEventId.toString());

        mockMvc.perform(multipart("/api/photos/upload")
                        .file(jpeg(new byte[]{1, 2, 3, 4}))
                        .param("eventId", ghostEventId.toString())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound());

        assertThat(photoRepository.findAll()).isEmpty();
    }

    // ─── Moderação: DELETE /api/photos/{photoId} (host) ───
    @Test
    void delete_hostOwner_hidesPhotoFromGalleryButPoseStaysSpent() throws Exception {
        UUID hostId = UUID.randomUUID();
        Event limited = eventRepository.save(Event.builder()
                .hostId(hostId)
                .name("Festa moderada")
                .slug("moderada-" + UUID.randomUUID().toString().substring(0, 8))
                .status(EventStatus.ACTIVE)
                .revealAt(Instant.now().plus(1, ChronoUnit.DAYS))
                .photoLimitPerGuest(1)
                .build());
        String hostToken = jwtUtil.generateHostToken(hostId.toString(), "host@eterniza.com");
        String guest = jwtUtil.generateGuestToken("device-abc", "Ana", limited.getId().toString());

        mockMvc.perform(multipart("/api/photos/upload")
                        .file(jpeg(new byte[]{1, 2, 3}))
                        .param("eventId", limited.getId().toString())
                        .header("Authorization", "Bearer " + guest))
                .andExpect(status().isCreated());
        UUID photoId = photoRepository.findAll().get(0).getId();

        mockMvc.perform(delete("/api/photos/{photoId}", photoId)
                        .header("Authorization", "Bearer " + hostToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        // Some da galeria (soft delete)...
        mockMvc.perform(get("/api/photos/gallery/{eventId}", limited.getId().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalPhotos").value(0));

        // ...mas a pose continua gasta: novo upload do mesmo device ainda é 400
        mockMvc.perform(multipart("/api/photos/upload")
                        .file(jpeg(new byte[]{4, 5, 6}))
                        .param("eventId", limited.getId().toString())
                        .header("Authorization", "Bearer " + guest))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Você já usou todas as suas 1 fotos neste evento"));

        assertThat(photoRepository.findById(photoId).orElseThrow().getStatus())
                .isEqualTo(PhotoStatus.DELETED);
    }

    @Test
    void delete_notOwner_returns403AndPhotoStays() throws Exception {
        Photo photo = photoRepository.save(readyPhoto("orig-x", null));
        String intruderToken = jwtUtil.generateHostToken(UUID.randomUUID().toString(), "intruso@eterniza.com");

        mockMvc.perform(delete("/api/photos/{photoId}", photo.getId())
                        .header("Authorization", "Bearer " + intruderToken))
                .andExpect(status().isForbidden());

        assertThat(photoRepository.findById(photo.getId()).orElseThrow().getStatus())
                .isEqualTo(PhotoStatus.READY);
    }

    @Test
    void delete_nonExistentPhoto_returns404() throws Exception {
        String hostToken = jwtUtil.generateHostToken(UUID.randomUUID().toString(), "host@eterniza.com");

        mockMvc.perform(delete("/api/photos/{photoId}", UUID.randomUUID())
                        .header("Authorization", "Bearer " + hostToken))
                .andExpect(status().isNotFound());
    }

    // ─── Moderação: GET /api/photos/event/{eventId} (host) ───
    @Test
    void listForHost_beforeReveal_returnsMetadataWithNullUrls() throws Exception {
        Event event = eventRepository.findById(eventId).orElseThrow();
        String hostToken = jwtUtil.generateHostToken(event.getHostId().toString(), "host@eterniza.com");
        photoRepository.save(readyPhoto("orig-1", null));

        mockMvc.perform(get("/api/photos/event/{eventId}", eventId.toString())
                        .header("Authorization", "Bearer " + hostToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].photoId").exists())
                .andExpect(jsonPath("$.data[0].guestName").value("Ana"))
                // Antes da revelação nem o host vê a imagem
                .andExpect(jsonPath("$.data[0].url").isEmpty());
    }

    @Test
    void listForHost_notOwner_returns403() throws Exception {
        String intruderToken = jwtUtil.generateHostToken(UUID.randomUUID().toString(), "intruso@eterniza.com");

        mockMvc.perform(get("/api/photos/event/{eventId}", eventId.toString())
                        .header("Authorization", "Bearer " + intruderToken))
                .andExpect(status().isForbidden());
    }

    // ─── IT-PHOTO-04: Gallery visibility (public) ───
    @Test
    void gallery_notRevealedEvent_isPublicAndHidesUrls() throws Exception {
        photoRepository.save(readyPhoto("orig-1", "filt-1"));

        mockMvc.perform(get("/api/photos/gallery/{eventId}", eventId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.revealed").value(false))
                .andExpect(jsonPath("$.data.totalPhotos").value(1))
                .andExpect(jsonPath("$.data.photoUrls").isEmpty());
    }

    @Test
    void gallery_revealedEvent_exposesPhotoUrls() throws Exception {
        Event revealed = eventRepository.findById(eventId).orElseThrow();
        revealed.setStatus(EventStatus.REVEALED);
        eventRepository.save(revealed);

        photoRepository.save(readyPhoto("orig-1", "filt-1"));
        photoRepository.save(readyPhoto("orig-2", null));

        when(storageService.publicUrlFor(anyString()))
                .thenAnswer(inv -> "https://cdn.eterniza.test/" + inv.getArgument(0));

        // A galeria expõe URLs públicas completas, não chaves de storage
        mockMvc.perform(get("/api/photos/gallery/{eventId}", eventId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.revealed").value(true))
                .andExpect(jsonPath("$.data.totalPhotos").value(2))
                .andExpect(jsonPath("$.data.photoUrls", containsInAnyOrder(
                        "https://cdn.eterniza.test/filt-1",
                        "https://cdn.eterniza.test/orig-2")));
    }

    private Photo readyPhoto(String originalKey, String filteredKey) {
        return Photo.builder()
                .eventId(eventId)
                .guestDeviceId("device-abc")
                .guestName("Ana")
                .originalKey(originalKey)
                .filteredKey(filteredKey)
                .status(PhotoStatus.READY)
                .build();
    }
}
