
package com.visualsearch.repository;

import com.visualsearch.entity.ImageOcr;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ImageOcrRepository extends JpaRepository<ImageOcr, UUID> {

    @Query(value = """
        WITH query_tokens AS (
            SELECT DISTINCT token
            FROM regexp_split_to_table(
                LOWER(TRIM(:query)), '\\s+'
            ) AS token
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
        )
        SELECT DISTINCT ON (o.image_id) o.*
        FROM image_ocr o
        JOIN matched_images m ON m.image_id = o.image_id
        WHERE o.normalized_text IS NOT NULL
          AND o.normalized_text <> ''
        ORDER BY o.image_id, o.confidence_score DESC NULLS LAST
        """, nativeQuery = true)
    List<ImageOcr> searchByNormalizedText(
            @Param("query") String query
    );
}
