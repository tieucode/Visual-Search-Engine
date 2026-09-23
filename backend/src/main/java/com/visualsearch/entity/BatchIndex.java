package com.visualsearch.entity;

import com.visualsearch.enums.BatchStatus;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "batch_index")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BatchIndex extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "uploaded_by")
    private User uploadedBy;

    @Column(name = "total_images", nullable = false)
    @Builder.Default
    private Integer totalImages = 0;

    @Column(name = "success_count", nullable = false)
    @Builder.Default
    private Integer successCount = 0;

    @Column(name = "failed_count", nullable = false)
    @Builder.Default
    private Integer failedCount = 0;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 20, nullable = false)
    private BatchStatus status;

    @Column(name = "total_duration_ms")
    private Long totalDurationMs;
}
