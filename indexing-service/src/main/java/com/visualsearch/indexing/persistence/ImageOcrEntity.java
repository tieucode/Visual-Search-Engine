package com.visualsearch.indexing.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "image_ocr")
@Getter
@Setter
@NoArgsConstructor
public class ImageOcrEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "image_id", nullable = false)
    private UUID imageId;

    @Column(name = "raw_text", columnDefinition = "TEXT")
    private String rawText;

    @Column(name = "normalized_text", columnDefinition = "TEXT")
    private String normalizedText;

    @Column(name = "confidence_score")
    private Float confidenceScore;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "bounding_box", columnDefinition = "jsonb")
    private BoundingBox boundingBox;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BoundingBox {
        private Float x;
        private Float y;
        private Float width;
        private Float height;
    }
}
