package com.eterniza.photo.service;

import com.eterniza.common.exception.BusinessException;
import com.eterniza.common.security.JwtUtil;
import com.eterniza.photo.domain.Photo;
import com.eterniza.photo.domain.PhotoStatus;
import com.eterniza.photo.dto.GalleryResponse;
import com.eterniza.photo.dto.PhotoUploadResponse;
import com.eterniza.photo.repository.PhotoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class PhotoServiceTest {

    private PhotoService photoService;

    @Mock private PhotoRepository photoRepository;
    @Mock private StorageService storageService;
    @Mock private JwtUtil jwtUtil;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        photoService = new PhotoService(photoRepository, storageService, jwtUtil);
    }

    @Test
    void upload_emptyFile_throwsBusinessException() throws IOException {
        MultipartFile file = mock(MultipartFile.class);
        when(file.isEmpty()).thenReturn(true);

        assertThatThrownBy(() -> photoService.upload(file, "Bearer token", "event-id"))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Arquivo vazio");
    }

    @Test
    void upload_invalidContentType_throwsBusinessException() throws IOException {
        MultipartFile file = mock(MultipartFile.class);
        when(file.isEmpty()).thenReturn(false);
        when(file.getContentType()).thenReturn("application/pdf");

        assertThatThrownBy(() -> photoService.upload(file, "Bearer token", "event-id"))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Formato inválido. Envie JPEG, PNG ou WebP");
    }

    @Test
    void upload_fileTooLarge_throwsBusinessException() throws IOException {
        MultipartFile file = mock(MultipartFile.class);
        when(file.isEmpty()).thenReturn(false);
        when(file.getContentType()).thenReturn("image/jpeg");
        when(file.getSize()).thenReturn(25 * 1024 * 1024L);

        assertThatThrownBy(() -> photoService.upload(file, "Bearer token", "event-id"))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Arquivo muito grande. Máximo 20MB");
    }

    @Test
    void upload_validFile_storesPhotoAsReady() throws IOException {
        UUID eventId = UUID.randomUUID();
        UUID photoId = UUID.randomUUID();
        String guestDeviceId = "device-123";
        String guestName = "Maria";
        String token = "Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...";

        MultipartFile file = mock(MultipartFile.class);
        when(file.isEmpty()).thenReturn(false);
        when(file.getContentType()).thenReturn("image/jpeg");
        when(file.getSize()).thenReturn(5 * 1024 * 1024L);

        var claims = mock(io.jsonwebtoken.Claims.class);
        when(jwtUtil.extractClaims(anyString())).thenReturn(claims);
        when(claims.getSubject()).thenReturn(guestDeviceId);
        when(claims.get("displayName")).thenReturn(guestName);

        Photo savedPhoto = Photo.builder()
                .id(photoId)
                .eventId(eventId)
                .guestDeviceId(guestDeviceId)
                .guestName(guestName)
                .status(PhotoStatus.READY)
                .build();
        when(photoRepository.save(any(Photo.class))).thenReturn(savedPhoto);

        PhotoUploadResponse response = photoService.upload(file, token, eventId.toString());

        assertThat(response.photoId()).isEqualTo(photoId);
        assertThat(response.message()).isEqualTo("Foto recebida!");
        verify(storageService).upload(contains("events/" + eventId + "/originals/"), eq(file));

        // A foto é persistida já pronta (o app enviou a imagem final filtrada) —
        // sem passo de processamento assíncrono no servidor.
        ArgumentCaptor<Photo> saved = ArgumentCaptor.forClass(Photo.class);
        verify(photoRepository).save(saved.capture());
        assertThat(saved.getValue().getStatus()).isEqualTo(PhotoStatus.READY);
        assertThat(saved.getValue().getEventId()).isEqualTo(eventId);
        assertThat(saved.getValue().getGuestDeviceId()).isEqualTo(guestDeviceId);
        assertThat(saved.getValue().getGuestName()).isEqualTo(guestName);
        assertThat(saved.getValue().getOriginalKey())
                .startsWith("events/" + eventId + "/originals/")
                .endsWith(".jpg");
    }

    @Test
    void getGallery_notRevealed_returnsEmptyUrls() {
        UUID eventId = UUID.randomUUID();
        Photo photo = Photo.builder()
                .id(UUID.randomUUID())
                .eventId(eventId)
                .status(PhotoStatus.READY)
                .filteredKey("filtered-key")
                .build();
        when(photoRepository.findByEventIdAndStatus(eventId, PhotoStatus.READY))
                .thenReturn(List.of(photo));

        GalleryResponse response = photoService.getGallery(eventId.toString(), false);

        assertThat(response.revealed()).isFalse();
        assertThat(response.totalPhotos()).isEqualTo(1);
        assertThat(response.photoUrls()).isEmpty();
    }

    @Test
    void getGallery_revealed_returnsPhotoUrls() {
        UUID eventId = UUID.randomUUID();
        Photo photo1 = Photo.builder()
                .id(UUID.randomUUID())
                .eventId(eventId)
                .status(PhotoStatus.READY)
                .originalKey("orig-1")
                .filteredKey("filtered-1")
                .build();
        Photo photo2 = Photo.builder()
                .id(UUID.randomUUID())
                .eventId(eventId)
                .status(PhotoStatus.READY)
                .originalKey("orig-2")
                .filteredKey(null)
                .build();
        when(photoRepository.findByEventIdAndStatus(eventId, PhotoStatus.READY))
                .thenReturn(List.of(photo1, photo2));
        when(storageService.publicUrlFor(anyString()))
                .thenAnswer(inv -> "https://cdn.eterniza.test/" + inv.getArgument(0));

        GalleryResponse response = photoService.getGallery(eventId.toString(), true);

        assertThat(response.revealed()).isTrue();
        assertThat(response.totalPhotos()).isEqualTo(2);
        // URLs públicas completas, não chaves de storage
        assertThat(response.photoUrls()).containsExactly(
                "https://cdn.eterniza.test/filtered-1",
                "https://cdn.eterniza.test/orig-2");
    }

    @Test
    void getGallery_noPhotos_returnsEmpty() {
        UUID eventId = UUID.randomUUID();
        when(photoRepository.findByEventIdAndStatus(eventId, PhotoStatus.READY))
                .thenReturn(List.of());

        GalleryResponse response = photoService.getGallery(eventId.toString(), true);

        assertThat(response.totalPhotos()).isZero();
        assertThat(response.photoUrls()).isEmpty();
    }
}
