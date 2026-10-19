
package com.visualsearch.repository;

import com.visualsearch.entity.ImageOcr;
import com.visualsearch.repository.projection.OcrSearchProjection;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ImageOcrRepository extends JpaRepository<ImageOcr, UUID> {

    List<ImageOcr> findByIdIsNotNull(Pageable pageable);

    List<ImageOcr> findByIdGreaterThanOrderByIdAsc(UUID id, Pageable pageable);

    @Query(value = """
        WITH query_tokens AS (
            SELECT DISTINCT token
            FROM regexp_split_to_table(
                LOWER(TRIM(:query)), '\\s+'
            ) AS split(token)
            WHERE token <> ''
        ),
        matched_images AS (
            SELECT o.image_id
            FROM image_ocr o
            JOIN images i ON i.id = o.image_id
            WHERE i.deleted_at IS NULL
              AND o.normalized_text IS NOT NULL
              AND o.normalized_text <> ''
            GROUP BY o.image_id
            HAVING NOT EXISTS (
                SELECT 1
                FROM query_tokens qt
                WHERE NOT EXISTS (
                    SELECT 1
                    FROM image_ocr o2
                    WHERE o2.image_id = o.image_id
                      AND o2.normalized_text IS NOT NULL
                      AND POSITION(
                          qt.token IN LOWER(o2.normalized_text)
                      ) > 0
                )
            )
        ), ocr_by_image AS (
            SELECT o.image_id,
                   COALESCE(
                       json_agg(o.raw_text ORDER BY o.confidence_score DESC NULLS LAST)
                           FILTER (WHERE o.raw_text IS NOT NULL),
                       CAST('[]' AS json)
                   ) AS raw_texts_json,
                   CAST(MAX(o.confidence_score) AS real) AS confidence_score
            FROM image_ocr o
            JOIN matched_images m ON m.image_id = o.image_id
            GROUP BY o.image_id
        )
        SELECT i.id AS "imageId",
               i.path AS "imagePath",
               i.thumbnail_path AS "thumbnailPath",
               i.width AS "width",
               i.height AS "height",
               CAST(o.raw_texts_json AS text) AS "rawTexts",
               o.confidence_score AS "confidenceScore"
        FROM ocr_by_image o
        JOIN images i ON i.id = o.image_id
        WHERE i.deleted_at IS NULL
        ORDER BY i.id
        LIMIT :limit OFFSET :offset
        """, nativeQuery = true)
    List<OcrSearchProjection> searchByNormalizedTextPage(
            @Param("query") String query,
            @Param("limit") int limit,
            @Param("offset") long offset
    );
}
