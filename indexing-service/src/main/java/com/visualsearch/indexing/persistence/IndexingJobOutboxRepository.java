package com.visualsearch.indexing.persistence;

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
public class IndexingJobOutboxRepository {
    private final NamedParameterJdbcTemplate jdbc;

    public List<OutboxJob> claimReady(int limit) {
        Instant now = Instant.now();
        return jdbc.query("""
                with candidates as (
                    select image_id from indexing_job_outbox
                    where status = 'PENDING' and next_attempt_at <= :now
                    order by next_attempt_at, image_id
                    limit :limit for update skip locked
                )
                update indexing_job_outbox x set status = 'PROCESSING', updated_at = :now
                from candidates c where x.image_id = c.image_id
                returning x.image_id, x.image_url, x.retry_count
                """, Map.of("now", Timestamp.from(now), "limit", limit),
                (rs, row) -> new OutboxJob(rs.getObject("image_id", UUID.class), rs.getString("image_url"), rs.getInt("retry_count")));
    }

    public int releaseStaleClaims(Duration timeout) {
        Instant now = Instant.now();
        return jdbc.update("""
                update indexing_job_outbox set status = 'PENDING', updated_at = :now
                where status = 'PROCESSING' and updated_at < :cutoff
                """, Map.of("now", Timestamp.from(now), "cutoff", Timestamp.from(now.minus(timeout))));
    }

    public void markPublished(List<UUID> ids) {
        if (ids.isEmpty()) return;
        jdbc.update("""
                update indexing_job_outbox set status = 'PUBLISHED', last_error = null, updated_at = :now
                where image_id in (:ids) and status = 'PROCESSING'
                """, Map.of("ids", ids, "now", Timestamp.from(Instant.now())));
    }

    public void markRetry(List<UUID> ids, String error, Duration delay) {
        if (ids.isEmpty()) return;
        Instant now = Instant.now();
        String message = error == null ? "RabbitMQ publish failed" : error;
        jdbc.update("""
                update indexing_job_outbox
                set status = 'PENDING', retry_count = retry_count + 1, last_error = :error,
                    next_attempt_at = :nextAttempt, updated_at = :now
                where image_id in (:ids) and status = 'PROCESSING'
                """, new MapSqlParameterSource("ids", ids)
                .addValue("error", message.substring(0, Math.min(2000, message.length())))
                .addValue("nextAttempt", Timestamp.from(now.plus(delay)))
                .addValue("now", Timestamp.from(now)));
    }

    public record OutboxJob(UUID imageId, String imageUrl, int retryCount) {}
}
