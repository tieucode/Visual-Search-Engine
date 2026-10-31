package com.visualsearch.indexing.persistence;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class IndexingJdbcRepository {
    private final NamedParameterJdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    public Map<UUID, ImageState> findByImageIds(Collection<UUID> ids) {
        if (ids.isEmpty()) return Map.of();
        var rows =
                jdbc.query(
                        "select image_id, batch_id, status from image_index where image_id in"
                                + " (:ids)",
                        Map.of("ids", ids),
                        (rs, row) ->
                                new ImageState(
                                        rs.getObject("image_id", UUID.class),
                                        rs.getObject("batch_id", UUID.class),
                                        IndexStatus.valueOf(rs.getString("status"))));
        Map<UUID, ImageState> states = new HashMap<>();
        rows.forEach(state -> states.put(state.imageId(), state));
        return states;
    }

    public void markRetryStarted(Collection<UUID> ids, int attempt) {
        jdbc.update(
                """
                with locked as (
                    select id from image_index where image_id in (:ids) and status = 'PENDING'
                    order by image_id for update
                )
                update image_index i set retry_count = greatest(i.retry_count, :attempt), updated_at = :now
                from locked where i.id = locked.id
                """,
                new MapSqlParameterSource("ids", ids)
                        .addValue("attempt", attempt)
                        .addValue("now", Timestamp.from(Instant.now())));
    }

    public Map<UUID, IndexStatus> recordOutcomes(Collection<IndexWrite> outcomes) {
        Instant now = Instant.now();
        var parameters = new MapSqlParameterSource("now", Timestamp.from(now));
        var writes = new LinkedHashMap<UUID, IndexWrite>();
        outcomes.stream()
                .sorted(Comparator.comparing(IndexWrite::imageId))
                .forEach(write -> writes.put(write.imageId(), write));
        List<String> values = new ArrayList<>();
        int position = 0;
        for (IndexWrite write : writes.values()) {
            String suffix = Integer.toString(position++);
            values.add(
                    "(cast(:id"
                            + suffix
                            + " as uuid), cast(:status"
                            + suffix
                            + " as varchar), cast(:attempt"
                            + suffix
                            + " as integer), cast(:error"
                            + suffix
                            + " as text))");
            parameters
                    .addValue("id" + suffix, write.imageId())
                    .addValue("status" + suffix, write.status().name())
                    .addValue("attempt" + suffix, write.attempt())
                    .addValue("error" + suffix, sanitize(write.error()));
        }
        String sql =
                """
                                with incoming(image_id, status, attempt, error_message) as (values %s),
                                locked as (
                                    select i.id from image_index i join incoming v on i.image_id = v.image_id
                                    where i.status = 'PENDING' order by i.image_id for update of i
                                )
                                update image_index i
                                set status = v.status, retry_count = greatest(i.retry_count, v.attempt),
                                    error_message = v.error_message, updated_at = :now
                                from incoming v, locked
                                where i.id = locked.id and i.image_id = v.image_id and i.status = 'PENDING'
                                returning i.image_id, i.batch_id, i.status
                """
                        .formatted(String.join(",", values));
        List<ImageState> changed =
                jdbc.query(
                        sql,
                        parameters,
                        (rs, row) ->
                                new ImageState(
                                        rs.getObject("image_id", UUID.class),
                                        rs.getObject("batch_id", UUID.class),
                                        IndexStatus.valueOf(rs.getString("status"))));
        List<UUID> successes =
                changed.stream()
                        .filter(row -> row.status() == IndexStatus.SUCCESS)
                        .map(ImageState::imageId)
                        .toList();
        replaceOcr(successes, writes, now);
        Map<UUID, int[]> counts = new TreeMap<>();
        Map<UUID, IndexStatus> applied = new HashMap<>();
        for (ImageState row : changed) {
            applied.put(row.imageId(), row.status());
            if (row.batchId() != null && row.status() != IndexStatus.PENDING) {
                counts.computeIfAbsent(row.batchId(), ignored -> new int[2])[
                        row.status() == IndexStatus.SUCCESS ? 0 : 1]++;
            }
        }
        for (var entry : counts.entrySet()) {
            jdbc.update(
                    """
                    update batch_index
                    set success_count = success_count + :success,
                        failed_count = failed_count + :failed,
                        status = case when status <> 'UPLOADING'
                            and success_count + failed_count + :success + :failed >= total_images
                            then 'COMPLETED' else status end,
                        total_duration_ms = case when status <> 'UPLOADING'
                            and success_count + failed_count + :success + :failed >= total_images
                            and created_at is not null
                            then cast(trunc(extract(epoch from (cast(:now as timestamptz) - created_at)) * 1000) as bigint)
                            else total_duration_ms end,
                        updated_at = :now
                    where id = :id
                    """,
                    new MapSqlParameterSource("id", entry.getKey())
                            .addValue("success", entry.getValue()[0])
                            .addValue("failed", entry.getValue()[1])
                            .addValue("now", Timestamp.from(now)));
        }
        return applied;
    }

    private void replaceOcr(List<UUID> successes, Map<UUID, IndexWrite> writes, Instant now) {
        if (successes.isEmpty()) return;
        jdbc.update("delete from image_ocr where image_id in (:ids)", Map.of("ids", successes));
        List<MapSqlParameterSource> rows = new ArrayList<>(100);
        for (UUID id : successes) {
            for (var item : writes.get(id).ocr()) {
                rows.add(
                        new MapSqlParameterSource("id", UUID.randomUUID())
                                .addValue("imageId", id)
                                .addValue("raw", item.text())
                                .addValue("normalized", item.normalizedText())
                                .addValue("confidence", item.confidence())
                                .addValue("box", json(item.boundingBox()))
                                .addValue("now", Timestamp.from(now)));
                if (rows.size() == 100) {
                    insertOcr(rows);
                    rows.clear();
                }
            }
        }
        if (!rows.isEmpty()) insertOcr(rows);
    }

    private void insertOcr(List<MapSqlParameterSource> rows) {
        jdbc.batchUpdate(
                """
                insert into image_ocr(id, image_id, raw_text, normalized_text, confidence_score,
                                      bounding_box, created_at, updated_at)
                values (:id, :imageId, :raw, :normalized, :confidence, cast(:box as jsonb), :now, :now)
                """,
                rows.toArray(MapSqlParameterSource[]::new));
    }

    private String json(Object value) {
        if (value == null) return null;
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Invalid OCR bounding box", exception);
        }
    }

    private String sanitize(String value) {
        if (value == null) return null;
        String message = value.isBlank() ? "Unknown indexing error" : value;
        return message.substring(0, Math.min(message.length(), 4000));
    }
}
