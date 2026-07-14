package com.eterniza.photo.service;

import com.eterniza.common.exception.BusinessException;
import com.eterniza.common.security.JwtUtil;
import com.eterniza.photo.domain.Photo;
import com.eterniza.photo.domain.PhotoStatus;
import com.eterniza.photo.dto.GalleryResponse;
import com.eterniza.photo.dto.PhotoUploadResponse;
import com.eterniza.photo.repository.PhotoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PhotoService {

    private final PhotoRepository photoRepository;
    private final StorageService storageService;
    private final JwtUtil jwtUtil;

    private static final List<String> ALLOWED = List.of("image/jpeg", "image/png", "image/webp");

    @Transactional
    public PhotoUploadResponse upload(MultipartFile file, String guestToken, String eventId) throws IOException {
        if (file.isEmpty())                             throw new BusinessException("Arquivo vazio");
        if (!ALLOWED.contains(file.getContentType()))  throw new BusinessException("Formato inválido. Envie JPEG, PNG ou WebP");
        if (file.getSize() > 20 * 1024 * 1024)        throw new BusinessException("Arquivo muito grande. Máximo 20MB");

        var claims    = jwtUtil.extractClaims(guestToken.replace("Bearer ", ""));
        String deviceId  = claims.getSubject();
        String guestName = (String) claims.get("displayName");

        // O filtro é aplicado ao vivo no app (client-side); o servidor recebe a
        // imagem já finalizada, apenas armazena e marca como pronta — sem
        // processamento assíncrono nem filtro no servidor.
        String photoId     = UUID.randomUUID().toString();
        String originalKey = "events/%s/originals/%s.jpg".formatted(eventId, photoId);

        storageService.upload(originalKey, file);

        Photo photo = photoRepository.save(Photo.builder()
                .eventId(UUID.fromString(eventId))
                .guestDeviceId(deviceId)
                .guestName(guestName)
                .originalKey(originalKey)
                .status(PhotoStatus.READY)
                .build());

        return new PhotoUploadResponse(photo.getId(), "Foto recebida!");
    }

    public GalleryResponse getGallery(String eventId, boolean isRevealed) {
        List<Photo> photos = photoRepository.findByEventIdAndStatus(
                UUID.fromString(eventId), PhotoStatus.READY);

        if (!isRevealed) {
            return new GalleryResponse(false, photos.size(), List.of());
        }

        List<String> urls = photos.stream()
                .map(p -> p.getFilteredKey() != null ? p.getFilteredKey() : p.getOriginalKey())
                .toList();

        return new GalleryResponse(true, photos.size(), urls);
    }
}
