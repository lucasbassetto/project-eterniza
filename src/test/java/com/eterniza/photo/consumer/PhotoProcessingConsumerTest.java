package com.eterniza.photo.consumer;

import com.eterniza.photo.domain.Photo;
import com.eterniza.photo.domain.PhotoStatus;
import com.eterniza.photo.filter.FilmFilterService;
import com.eterniza.photo.repository.PhotoRepository;
import com.eterniza.photo.service.StorageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.io.IOException;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class PhotoProcessingConsumerTest {

    private PhotoProcessingConsumer consumer;

    @Mock private PhotoRepository photoRepository;
    @Mock private StorageService storageService;
    @Mock private FilmFilterService filmFilterService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        consumer = new PhotoProcessingConsumer(photoRepository, storageService, filmFilterService);
    }

    private Map<String, String> msg(UUID photoId, String originalKey, String filmStyle) {
        return Map.of("photoId", photoId.toString(), "originalKey", originalKey, "filmStyle", filmStyle);
    }

    @Test
    void process_validPhoto_appliesFilterAndMarksReady() throws Exception {
        UUID photoId = UUID.randomUUID();
        String originalKey = "events/E/originals/" + photoId + ".jpg";
        Photo photo = Photo.builder().id(photoId).status(PhotoStatus.PROCESSING).build();

        byte[] originalBytes = {1, 2, 3};
        byte[] filteredBytes = {9, 8, 7};
        when(photoRepository.findById(photoId)).thenReturn(Optional.of(photo));
        when(storageService.download(originalKey)).thenReturn(originalBytes);
        when(filmFilterService.apply(originalBytes, "VINTAGE")).thenReturn(filteredBytes);

        consumer.process(msg(photoId, originalKey, "VINTAGE"));

        String expectedFilteredKey = "events/E/filtered/" + photoId + ".jpg";
        verify(storageService).upload(eq(expectedFilteredKey), eq(filteredBytes), eq("image/jpeg"));

        ArgumentCaptor<Photo> saved = ArgumentCaptor.forClass(Photo.class);
        verify(photoRepository).save(saved.capture());
        assertThat(saved.getValue().getStatus()).isEqualTo(PhotoStatus.READY);
        assertThat(saved.getValue().getFilteredKey()).isEqualTo(expectedFilteredKey);
    }

    @Test
    void process_downloadFails_marksFailedAndPersists() throws Exception {
        UUID photoId = UUID.randomUUID();
        String originalKey = "events/E/originals/" + photoId + ".jpg";
        Photo photo = Photo.builder().id(photoId).status(PhotoStatus.PROCESSING).build();

        when(photoRepository.findById(photoId)).thenReturn(Optional.of(photo));
        when(storageService.download(originalKey)).thenThrow(new IOException("R2 down"));

        consumer.process(msg(photoId, originalKey, "VINTAGE"));

        verify(storageService, never()).upload(anyString(), any(byte[].class), anyString());

        ArgumentCaptor<Photo> saved = ArgumentCaptor.forClass(Photo.class);
        verify(photoRepository).save(saved.capture());
        assertThat(saved.getValue().getStatus()).isEqualTo(PhotoStatus.FAILED);
        assertThat(saved.getValue().getFilteredKey()).isNull();
    }

    @Test
    void process_photoNotFound_returnsWithoutTouchingStorage() throws Exception {
        UUID photoId = UUID.randomUUID();
        when(photoRepository.findById(photoId)).thenReturn(Optional.empty());

        consumer.process(msg(photoId, "events/E/originals/" + photoId + ".jpg", "VINTAGE"));

        verifyNoInteractions(storageService, filmFilterService);
        verify(photoRepository, never()).save(any());
    }
}
