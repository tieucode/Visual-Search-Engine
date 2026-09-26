package com.visualsearch.entity;

import com.visualsearch.enums.IndexStatus;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(
        name = "image_index",
        uniqueConstraints = @UniqueConstraint(name = "uk_image_index_image_id", columnNames = "image_id"))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ImageIndex extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "image_id", nullable = false)
    private Image image;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "batch_id")
    private BatchIndex batch;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 20, nullable = false)
    private IndexStatus status;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "retry_count", nullable = false)
    @Builder.Default
    private Integer retryCount = 0;
}
