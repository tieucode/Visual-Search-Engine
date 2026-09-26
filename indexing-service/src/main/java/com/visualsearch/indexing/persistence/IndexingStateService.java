package com.visualsearch.indexing.persistence;

import com.visualsearch.indexing.processing.OcrData;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class IndexingStateService {

    private static final int MAX_ERROR_LENGTH = 4000;

    private final ImageIndexRepository imageIndexRepository;
    private final ImageOcrRepository imageOcrRepository;
    private final BatchIndexRepository batchIndexRepository;

    @Transactional(readOnly = true)
    public Map<UUID, ImageIndexEntity> findByImageIds(Collection<UUID> imageIds) {
        return imageIndexRepository.findByImageIdIn(imageIds).stream()
                .collect(Collectors.toMap(ImageIndexEntity::getImageId, Function.identity()));
    }

    @Transactional
    public boolean markRetryStarted(UUID imageId, int attempt) {
        return imageIndexRepository.markRetryStarted(imageId, attempt, Instant.now()) == 1;
    }

    @Transactional
    public boolean recordSuccess(UUID imageId, UUID batchId, List<OcrData> ocrItems) {
        Instant now = Instant.now();
        int transitioned = imageIndexRepository.markSuccess(imageId, now);
        if (transitioned == 0) {
            return false;
        }

        imageOcrRepository.deleteByImageId(imageId);
        List<ImageOcrEntity> entities = ocrItems.stream()
                .map(item -> toEntity(imageId, item, now))
                .toList();
        imageOcrRepository.saveAll(entities);
        if (batchId != null) {
            batchIndexRepository.incrementSuccess(batchId, now);
            completeBatchIfTerminal(batchId, now);
        }
        return true;
    }

    @Transactional
    public boolean recordPermanentImageFailure(UUID imageId, UUID batchId, String errorMessage) {
        Instant now = Instant.now();
        int transitioned = imageIndexRepository.markPermanentImageFailure(imageId, sanitize(errorMessage), now);
        return finishFailure(batchId, transitioned, now);
    }

    @Transactional
    public boolean recordPermanentFailure(
            UUID imageId,
            UUID batchId,
            int attempt,
            String errorMessage) {
        Instant now = Instant.now();
        int transitioned = imageIndexRepository.markPermanentFailure(
                imageId,
                attempt,
                sanitize(errorMessage),
                now);
        return finishFailure(batchId, transitioned, now);
    }

    private boolean finishFailure(UUID batchId, int transitioned, Instant now) {
        if (transitioned == 0) {
            return false;
        }
        if (batchId != null) {
            batchIndexRepository.incrementFailed(batchId, now);
            completeBatchIfTerminal(batchId, now);
        }
        return true;
    }

    @Transactional
    public boolean recordTransientFailure(UUID imageId, String errorMessage) {
        return imageIndexRepository.recordTransientFailure(imageId, sanitize(errorMessage), Instant.now()) == 1;
    }

    @Transactional
    public boolean recordRetriesExhausted(UUID imageId, UUID batchId, int attempt, String errorMessage) {
        Instant now = Instant.now();
        int transitioned = imageIndexRepository.markRetriesExhausted(
                imageId,
                attempt,
                sanitize(errorMessage),
                now);
        if (transitioned == 0) {
            return false;
        }
        if (batchId != null) {
            batchIndexRepository.incrementFailed(batchId, now);
            completeBatchIfTerminal(batchId, now);
        }
        return true;
    }

    private ImageOcrEntity toEntity(UUID imageId, OcrData item, Instant now) {
        ImageOcrEntity entity = new ImageOcrEntity();
        entity.setImageId(imageId);
        entity.setRawText(item.text());
        entity.setNormalizedText(item.normalizedText());
        entity.setConfidenceScore(item.confidence());
        if (item.boundingBox() != null) {
            entity.setBoundingBox(new ImageOcrEntity.BoundingBox(
                    item.boundingBox().x(),
                    item.boundingBox().y(),
                    item.boundingBox().width(),
                    item.boundingBox().height()));
        }
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);
        return entity;
    }

    private void completeBatchIfTerminal(UUID batchId, Instant now) {
        batchIndexRepository.findById(batchId).ifPresent(batch -> {
            if (batch.getStatus() != BatchStatus.UPLOADING
                    && batch.getSuccessCount() + batch.getFailedCount() >= batch.getTotalImages()) {
                batch.setStatus(BatchStatus.COMPLETED);
                batch.setUpdatedAt(now);
                if (batch.getCreatedAt() != null) {
                    batch.setTotalDurationMs(Duration.between(batch.getCreatedAt(), now).toMillis());
                }
                batchIndexRepository.save(batch);
            }
        });
    }

    private String sanitize(String errorMessage) {
        String value = errorMessage == null || errorMessage.isBlank() ? "Unknown indexing error" : errorMessage;
        return value.length() <= MAX_ERROR_LENGTH ? value : value.substring(0, MAX_ERROR_LENGTH);
    }
}
