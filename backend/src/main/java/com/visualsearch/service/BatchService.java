package com.visualsearch.service;

import com.visualsearch.dto.upload.BatchInitRequest;
import com.visualsearch.dto.upload.BatchInitData;
import com.visualsearch.dto.upload.BatchPageData;
import com.visualsearch.dto.upload.BatchStatusData;
import com.visualsearch.dto.upload.BatchSummaryData;
import com.visualsearch.entity.BatchIndex;
import com.visualsearch.entity.User;
import com.visualsearch.enums.BatchStatus;
import com.visualsearch.exception.BadRequestException;
import com.visualsearch.exception.ResourceNotFoundException;
import com.visualsearch.repository.BatchIndexRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Locale;
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
    public BatchStatusData getBatchStatus(UUID batchId, User user) {
        BatchIndex batch = getBatchEntity(batchId, user);
        return BatchStatusData.builder()
                .batchId(batch.getId())
                .totalImages(batch.getTotalImages())
                .processedImages(batch.getSuccessCount() + batch.getFailedCount())
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
    public BatchIndex getBatchEntity(UUID batchId, User user) {
        return batchIndexRepository.findByIdAndUploadedById(batchId, user.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Batch not found with id: " + batchId));
    }

    @Transactional(readOnly = true)
    public BatchPageData listBatches(User user, String status, String sort, int page, int size) {
        if (page < 0) {
            throw new BadRequestException("page must be at least 0");
        }
        if (size < 1 || size > 100) {
            throw new BadRequestException("size must be between 1 and 100");
        }

        BatchStatus batchStatus;
        try {
            batchStatus = BatchStatus.valueOf(status.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new BadRequestException("Unsupported batch status: " + status);
        }
        if (!"createdAt,desc".equalsIgnoreCase(sort)) {
            throw new BadRequestException("Only sort=createdAt,desc is supported");
        }
        Page<BatchSummaryData> batches = batchIndexRepository.findByUploadedByIdAndStatus(
                user.getId(), batchStatus, PageRequest.of(page, size,
                        Sort.by(Sort.Direction.DESC, "createdAt").and(Sort.by(Sort.Direction.DESC, "id"))))
                .map(this::toSummary);
        return new BatchPageData(
                batches.getContent(),
                batches.getTotalPages(),
                batches.getTotalElements(),
                batches.getNumber());
    }

    private BatchSummaryData toSummary(BatchIndex batch) {
        return new BatchSummaryData(
                batch.getId(),
                batch.getSuccessCount() + batch.getFailedCount(),
                batch.getTotalImages(),
                batch.getStatus(),
                batch.getCreatedAt());
    }

    @Transactional
    public void completeBatch(UUID batchId) {
        int updated = batchIndexRepository.closeUploadPhase(batchId, Instant.now());
        if (updated == 1) {
            BatchStatus status = batchIndexRepository.findById(batchId)
                    .map(BatchIndex::getStatus)
                    .orElseThrow(() -> new ResourceNotFoundException("Batch not found with id: " + batchId));
            log.info("Batch {} marked as {} (upload phase completed)", batchId, status);
        } else if (!batchIndexRepository.existsById(batchId)) {
            throw new ResourceNotFoundException("Batch not found with id: " + batchId);
        }
    }

    @Transactional
    public void recordUploadFailures(UUID batchId, int failedCount) {
        if (failedCount <= 0) {
            return;
        }
        int updated = batchIndexRepository.incrementFailed(batchId, failedCount, Instant.now());
        if (updated == 0) {
            throw new ResourceNotFoundException("Batch not found with id: " + batchId);
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
                completeBatch(batch.getId());
                log.warn("Batch {} timed out after {} mins without upload activity.",
                        batch.getId(), batchTimeoutMinutes);
            }
        }
    }
}
