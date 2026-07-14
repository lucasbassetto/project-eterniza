package com.eterniza.photo.controller;

import com.eterniza.common.security.JwtUtil;
import com.eterniza.event.domain.Event;
import com.eterniza.event.domain.EventStatus;
import com.eterniza.event.domain.FilmStyle;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
                .filmStyle(FilmStyle.VINTAGE)
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
                .andExpect(status().isAccepted())
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

        mockMvc.perform(get("/api/photos/gallery/{eventId}", eventId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.revealed").value(true))
                .andExpect(jsonPath("$.data.totalPhotos").value(2))
                .andExpect(jsonPath("$.data.photoUrls", containsInAnyOrder("filt-1", "orig-2")));
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
