package com.visualsearch.dto.upload;

import com.visualsearch.enums.BatchStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BatchStatusData {

    private UUID batchId;
    private Integer totalImages;
    private Integer processedImages;
    private Integer successCount;
    private Integer failedCount;
    private BatchStatus status;
    private Long totalDurationMs;
    private Instant createdAt;
    private Instant updatedAt;
}
