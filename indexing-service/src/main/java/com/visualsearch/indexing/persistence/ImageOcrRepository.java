package com.visualsearch.indexing.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface ImageOcrRepository extends JpaRepository<ImageOcrEntity, UUID> {

    @Modifying(flushAutomatically = true)
    void deleteByImageId(UUID imageId);

    /**
     * Tìm OCR chứa tất cả từ khóa, không yêu cầu đúng thứ tự.
     * Query phải được chuẩn hóa trước khi truyền vào.
     */
    @Query(value = """
        SELECT o.*
        FROM image_ocr o
        JOIN images i ON i.id = o.image_id
        WHERE i.deleted_at IS NULL
          AND o.normalized_text IS NOT NULL
          AND NOT EXISTS (
              SELECT 1
              FROM regexp_split_to_table(:query, '\\s+') AS token
              WHERE token <> ''
                AND o.normalized_text NOT LIKE '%' || token || '%'
          )
        """, nativeQuery = true)
    List<ImageOcrEntity> searchByNormalizedText(
            @Param("query") String query
    );
}
