package com.visualsearch.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "ocr_search_outbox", indexes = {
        @Index(name = "idx_ocr_search_outbox_ready", columnList = "status,next_attempt_at")
})
@Getter
@Setter
@NoArgsConstructor
public class OcrSearchOutbox {
    @Id
    @Column(name = "image_id", nullable = false)
    private UUID imageId;

    @Column(name = "status", nullable = false, length = 20)
    private String status = "PENDING";

    @Column(name = "retry_count", nullable = false)
    private int retryCount;

    @Column(name = "last_error", columnDefinition = "TEXT")
    private String lastError;

    @Column(name = "next_attempt_at", nullable = false)
    private Instant nextAttemptAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();
}
