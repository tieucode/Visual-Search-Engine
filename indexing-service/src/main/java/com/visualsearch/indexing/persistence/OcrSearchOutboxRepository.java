package com.visualsearch.indexing.persistence;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class OcrSearchOutboxRepository {
    private final NamedParameterJdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    public int enqueueMissing(int limit) {
        return jdbc.update("""
                with missing as (
                    select distinct o.image_id
                    from image_ocr o
                    join images i on i.id = o.image_id and i.deleted_at is null
                    where not exists (
                        select 1 from ocr_search_outbox x where x.image_id = o.image_id
                    )
                    order by o.image_id
                    limit :limit
                )
                insert into ocr_search_outbox(image_id, status, retry_count, next_attempt_at, updated_at)
                select image_id, 'PENDING', 0, :now, :now from missing
                on conflict (image_id) do nothing
                """, new MapSqlParameterSource("limit", limit).addValue("now", Timestamp.from(Instant.now())));
    }

    public int enqueueDeleted(int limit) {
        return jdbc.update("""
                with deleted as (
                    select x.image_id from ocr_search_outbox x join images i on i.id = x.image_id
                    where i.deleted_at is not null and x.status = 'INDEXED'
                    order by x.image_id limit :limit
                )
                update ocr_search_outbox x set status = 'PENDING', retry_count = 0,
                    last_error = null, next_attempt_at = :now, updated_at = :now
                from deleted d where x.image_id = d.image_id
                """, Map.of("limit", limit, "now", Timestamp.from(Instant.now())));
    }

    public List<OutboxEntry> claimReady(int limit) {
        return jdbc.query("""
                with candidates as (
                    select image_id from ocr_search_outbox
                    where status = 'PENDING' and next_attempt_at <= :now
                    order by next_attempt_at, image_id
                    limit :limit for update skip locked
                )
                update ocr_search_outbox x set status = 'PROCESSING', updated_at = :now
                from candidates c where x.image_id = c.image_id
                returning x.image_id, x.retry_count
                """, Map.of("now", Timestamp.from(Instant.now()), "limit", limit),
                (rs, row) -> new OutboxEntry(rs.getObject("image_id", UUID.class), rs.getInt("retry_count")));
    }

    public int releaseStaleClaims(Duration timeout) {
        Instant now = Instant.now();
        return jdbc.update("""
                update ocr_search_outbox set status = 'PENDING', updated_at = :now
                where status = 'PROCESSING' and updated_at < :cutoff
                """, Map.of("now", Timestamp.from(now), "cutoff", Timestamp.from(now.minus(timeout))));
    }

    public void releaseClaim(UUID imageId) {
        jdbc.update("update ocr_search_outbox set status = 'PENDING', updated_at = :now where image_id = :id and status = 'PROCESSING'",
                Map.of("id", imageId, "now", Timestamp.from(Instant.now())));
    }

    public boolean isProcessing(UUID imageId) {
        Boolean processing = jdbc.queryForObject("select exists(select 1 from ocr_search_outbox where image_id = :id and status = 'PROCESSING')",
                Map.of("id", imageId), Boolean.class);
        return Boolean.TRUE.equals(processing);
    }

    public List<OcrIndexDocument> loadDocuments(List<UUID> ids) {
        if (ids.isEmpty()) return List.of();
        return jdbc.query("""
                select i.id as image_id, i.path as image_path, i.thumbnail_path, i.width, i.height,
                       coalesce(json_agg(o.normalized_text order by o.confidence_score desc nulls last)
                           filter (where o.normalized_text is not null and o.normalized_text <> ''), '[]'::json)::text
                           as normalized_texts,
                       coalesce(json_agg(o.raw_text order by o.confidence_score desc nulls last)
                           filter (where o.raw_text is not null), '[]'::json)::text as raw_texts,
                       max(o.confidence_score) as confidence_score
                from images i left join image_ocr o on o.image_id = i.id
                where i.id in (:ids) and i.deleted_at is null
                group by i.id, i.path, i.thumbnail_path, i.width, i.height
                """, Map.of("ids", ids), (rs, row) -> new OcrIndexDocument(
                rs.getObject("image_id", UUID.class), rs.getString("image_path"), rs.getString("thumbnail_path"),
                (Integer) rs.getObject("width"), (Integer) rs.getObject("height"),
                readTexts(rs.getString("normalized_texts")), readTexts(rs.getString("raw_texts")),
                rs.getObject("confidence_score") == null ? null : rs.getDouble("confidence_score")));
    }

    public void markIndexed(UUID imageId) {
        jdbc.update("""
                update ocr_search_outbox set status = 'INDEXED', last_error = null, updated_at = :now
                where image_id = :id
                """, Map.of("id", imageId, "now", Timestamp.from(Instant.now())));
    }

    public void markDeleted(UUID imageId) {
        jdbc.update("""
                update ocr_search_outbox set status = 'DELETED', last_error = null, updated_at = :now
                where image_id = :id
                """, Map.of("id", imageId, "now", Timestamp.from(Instant.now())));
    }

    public void markRetry(UUID imageId, String error, Duration delay) {
        jdbc.update("""
                update ocr_search_outbox
                set status = 'PENDING', retry_count = retry_count + 1, last_error = :error,
                    next_attempt_at = :nextAttempt, updated_at = :now
                where image_id = :id and status in ('PENDING', 'PROCESSING')
                """, new MapSqlParameterSource("id", imageId)
                .addValue("error", error == null ? "Unknown Elasticsearch error" : error.substring(0, Math.min(2000, error.length())))
                .addValue("nextAttempt", Timestamp.from(Instant.now().plus(delay)))
                .addValue("now", Timestamp.from(Instant.now())));
    }

    private List<String> readTexts(String json) {
        try {
            return objectMapper.readValue(json, new TypeReference<>() {});
        } catch (Exception exception) {
            throw new IllegalStateException("Could not parse OCR values for Elasticsearch indexing", exception);
        }
    }

    public record OutboxEntry(UUID imageId, int retryCount) {}
}
