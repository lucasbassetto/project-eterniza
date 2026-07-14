package com.eterniza.photo.service;

import com.eterniza.common.exception.BusinessException;
import com.eterniza.common.exception.ForbiddenException;
import com.eterniza.common.exception.NotFoundException;
import com.eterniza.common.security.JwtUtil;
import com.eterniza.event.domain.Event;
import com.eterniza.event.repository.EventRepository;
import com.eterniza.photo.domain.Photo;
import com.eterniza.photo.domain.PhotoStatus;
import com.eterniza.photo.dto.EventPhotoResponse;
import com.eterniza.photo.dto.GalleryResponse;
import com.eterniza.photo.dto.PhotoUploadResponse;
import com.eterniza.photo.repository.PhotoRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class PhotoService {

    private final PhotoRepository photoRepository;
    private final EventRepository eventRepository;
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

        Event event = eventRepository.findById(UUID.fromString(eventId))
                .orElseThrow(() -> new NotFoundException("Evento", eventId));

        // Estilo "câmera descartável": cada convidado (deviceId) tem um número
        // fixo de poses no evento. Contagem direto da tabela de fotos.
        long photosTaken = photoRepository.countByEventIdAndGuestDeviceId(event.getId(), deviceId);
        if (photosTaken >= event.getPhotoLimitPerGuest()) {
            throw new BusinessException("Você já usou todas as suas %d fotos neste evento"
                    .formatted(event.getPhotoLimitPerGuest()));
        }

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

        int remaining = event.getPhotoLimitPerGuest() - (int) (photosTaken + 1);
        return new PhotoUploadResponse(photo.getId(), "Foto recebida!", remaining);
    }

    /**
     * Fotos do evento vistas pelo host (moderação). Antes da revelação as URLs
     * vêm nulas — a surpresa vale até para o host; ele modera pelos metadados.
     */
    public List<EventPhotoResponse> listForHost(String eventId, UUID hostId) {
        Event event = eventRepository.findById(UUID.fromString(eventId))
                .orElseThrow(() -> new NotFoundException("Evento", eventId));
        if (!event.getHostId().equals(hostId)) {
            throw new ForbiddenException("Você não é o dono deste evento");
        }

        return photoRepository.findByEventIdAndStatus(event.getId(), PhotoStatus.READY)
                .stream()
                .map(p -> new EventPhotoResponse(
                        p.getId(), p.getGuestName(), p.getCreatedAt(),
                        event.isRevealed()
                                ? storageService.publicUrlFor(
                                        p.getFilteredKey() != null ? p.getFilteredKey() : p.getOriginalKey())
                                : null))
                .toList();
    }

    /**
     * Moderação: só o host dono do evento apaga fotos. Soft delete — a foto some
     * da galeria, mas a linha permanece e a "pose" do convidado continua gasta.
     * Idempotente: apagar uma foto já apagada não faz nada.
     */
    @Transactional
    public void deleteAsHost(UUID photoId, UUID hostId) {
        Photo photo = photoRepository.findById(photoId)
                .orElseThrow(() -> new NotFoundException("Foto", photoId));
        Event event = eventRepository.findById(photo.getEventId())
                .orElseThrow(() -> new NotFoundException("Evento", photo.getEventId()));
        if (!event.getHostId().equals(hostId)) {
            throw new ForbiddenException("Você não é o dono deste evento");
        }
        if (photo.getStatus() == PhotoStatus.DELETED) return;

        photo.setStatus(PhotoStatus.DELETED);

        // Remoção do storage é best-effort: se o bucket falhar, a foto já não
        // aparece mais na galeria (status DELETED) e o objeto órfão não vaza.
        try {
            storageService.delete(photo.getOriginalKey());
            if (photo.getFilteredKey() != null) {
                storageService.delete(photo.getFilteredKey());
            }
        } catch (Exception e) {
            log.error("Falha ao remover do storage os arquivos da foto {} — a foto já está oculta da galeria", photoId, e);
        }
    }

    public GalleryResponse getGallery(String eventId, boolean isRevealed) {
        List<Photo> photos = photoRepository.findByEventIdAndStatus(
                UUID.fromString(eventId), PhotoStatus.READY);

        if (!isRevealed) {
            return new GalleryResponse(false, photos.size(), List.of());
        }

        // Devolve a URL pública completa (não a chave do storage) — o cliente
        // consome direto, sem precisar conhecer o bucket.
        List<String> urls = photos.stream()
                .map(p -> storageService.publicUrlFor(
                        p.getFilteredKey() != null ? p.getFilteredKey() : p.getOriginalKey()))
                .toList();

        return new GalleryResponse(true, photos.size(), urls);
    }
}
