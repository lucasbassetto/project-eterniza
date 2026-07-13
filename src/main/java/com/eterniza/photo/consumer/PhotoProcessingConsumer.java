package com.eterniza.photo.consumer;

import com.eterniza.photo.domain.Photo;
import com.eterniza.photo.domain.PhotoStatus;
import com.eterniza.photo.filter.FilmFilterService;
import com.eterniza.photo.repository.PhotoRepository;
import com.eterniza.photo.service.StorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;

import static com.eterniza.event.messaging.RabbitMQConfig.PHOTO_QUEUE;

@Slf4j
@Component
@RequiredArgsConstructor
public class PhotoProcessingConsumer {

    private final PhotoRepository photoRepository;
    private final StorageService storageService;
    private final FilmFilterService filmFilterService;

    @RabbitListener(queues = PHOTO_QUEUE)
    public void process(Map<String, String> msg) {
        String photoId     = msg.get("photoId");
        String originalKey = msg.get("originalKey");
        String filmStyle   = msg.get("filmStyle");

        log.info("Processando foto {} com filtro {}", photoId, filmStyle);

        Photo photo = photoRepository.findById(UUID.fromString(photoId)).orElse(null);
        if (photo == null) { log.warn("Foto não encontrada: {}", photoId); return; }

        try {
            byte[] original = storageService.download(originalKey);
            byte[] filtered = filmFilterService.apply(original, filmStyle);

            String filteredKey = originalKey.replace("/originals/", "/filtered/");
            storageService.upload(filteredKey, filtered, "image/jpeg");

            photo.setFilteredKey(filteredKey);
            photo.setStatus(PhotoStatus.READY);
        } catch (Exception e) {
            log.error("Erro ao processar foto {}", photoId, e);
            photo.setStatus(PhotoStatus.FAILED);
        }
        photoRepository.save(photo);
    }
}
