package com.visualsearch.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.io.Serializable;

@Entity
@Table(name = "image_ocr")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ImageOcr extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "image_id", nullable = false)
    private Image image;

    @Column(name = "raw_text", columnDefinition = "TEXT")
    private String rawText;

    @Column(name = "normalized_text", columnDefinition = "TEXT")
    private String normalizedText;

    @Column(name = "confidence_score")
    private Float confidenceScore;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "bounding_box", columnDefinition = "jsonb")
    private BoundingBox boundingBox;

    /**
     * Toạ độ khung chữ nhật bao quanh dòng/từ chữ tìm thấy trong ảnh.
     * Sử dụng toạ độ chuẩn hoá (0.0 đến 1.0) theo tỉ lệ chiều rộng/cao của ảnh
     * giúp Frontend vẽ trực tiếp bằng CSS (%) mà không cần tính toán scale.
     */
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class BoundingBox implements Serializable {
        private Float x;
        private Float y;
        private Float width;
        private Float height;
    }
}
