package com.visualsearch.service;

import com.visualsearch.dto.upload.BatchInitRequest;
import com.visualsearch.dto.upload.BatchInitData;
import com.visualsearch.dto.upload.BatchStatusData;
import com.visualsearch.entity.BatchIndex;
import com.visualsearch.entity.User;
import com.visualsearch.enums.BatchStatus;
import com.visualsearch.exception.ResourceNotFoundException;
import com.visualsearch.repository.BatchIndexRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class BatchService {

    private final BatchIndexRepository batchIndexRepository;

    @Value("${app.upload.batch-timeout-minutes:15}")
    private int batchTimeoutMinutes;

    // Khởi tạo luồng upload mới
    @Transactional
    public BatchInitData initBatch(BatchInitRequest request, User user) {
        BatchIndex batch = BatchIndex.builder()
                .uploadedBy(user)
                .totalImages(request.getTotalImages())
                .successCount(0)
                .failedCount(0)
                .status(BatchStatus.UPLOADING)
                .build();

        batch = batchIndexRepository.save(batch);
        log.info("Initialized batch {} with expected {} images", batch.getId(), batch.getTotalImages());
        return new BatchInitData(batch.getId());
    }

    // Lấy thông tin và trạng thái của batch
    @Transactional(readOnly = true)
    public BatchStatusData getBatchStatus(UUID batchId) {
        BatchIndex batch = getBatchEntity(batchId);
        return BatchStatusData.builder()
                .batchId(batch.getId())
                .totalImages(batch.getTotalImages())
                .successCount(batch.getSuccessCount())
                .failedCount(batch.getFailedCount())
                .status(batch.getStatus())
                .totalDurationMs(batch.getTotalDurationMs())
                .createdAt(batch.getCreatedAt())
                .updatedAt(batch.getUpdatedAt())
                .build();
    }

    // Lấy batchIndex theo ID
    @Transactional(readOnly = true)
    public BatchIndex getBatchEntity(UUID batchId) {
        return batchIndexRepository.findById(batchId)
                .orElseThrow(() -> new ResourceNotFoundException("Batch not found with id: " + batchId));
    }

    @Transactional
    public void completeBatch(BatchIndex batch) {
        if (batch.getStatus() == BatchStatus.UPLOADING) {
            if (batch.getSuccessCount() + batch.getFailedCount() >= batch.getTotalImages()) {
                batch.setStatus(BatchStatus.COMPLETED);
            } else {
                batch.setStatus(BatchStatus.PROCESSING);
            }

            batchIndexRepository.save(batch);
            log.info("Batch {} marked as {} (upload phase completed)", batch.getId(), batch.getStatus());
        }
    }

    // Định kỳ quét và tự động chuyển các batch bị treo quá timeout
    @Scheduled(fixedDelay = 60000) // Chạy mỗi 1 phút
    @Transactional
    public void autoCloseStuckBatches() {
        Instant threshold = Instant.now().minus(batchTimeoutMinutes, ChronoUnit.MINUTES);
        List<BatchIndex> stuckBatches = batchIndexRepository.findByStatusAndUpdatedAtBefore(
                BatchStatus.UPLOADING,
                threshold);

        if (!stuckBatches.isEmpty()) {
            for (BatchIndex batch : stuckBatches) {
                completeBatch(batch);
                log.warn("Batch {} timed out after {} mins without upload activity. Status set to {}.",
                        batch.getId(), batchTimeoutMinutes, batch.getStatus());
            }
        }
    }
}
