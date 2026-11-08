package com.visualsearch.service;

import com.visualsearch.dto.admin.OcrSearchIndexStatusData;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class OcrSearchReindexService {
    private final JdbcTemplate jdbc;

    @Transactional
    public int reindexAll() {
        return jdbc.update("""
                insert into ocr_search_outbox(image_id, status, retry_count, last_error, next_attempt_at, updated_at)
                select distinct o.image_id, 'PENDING', 0, null, now(), now()
                from image_ocr o join images i on i.id = o.image_id
                where i.deleted_at is null
                on conflict (image_id) do update set status = 'PENDING', retry_count = 0,
                    last_error = null, next_attempt_at = now(), updated_at = now()
                """);
    }

    @Transactional(readOnly = true)
    public OcrSearchIndexStatusData status() {
        return jdbc.queryForObject("""
                with total as (
                    select count(distinct o.image_id) as total
                    from image_ocr o join images i on i.id = o.image_id
                    where i.deleted_at is null
                ), states as (
                    select count(*) filter (where x.status = 'INDEXED') as indexed,
                           count(*) filter (where x.status = 'PENDING') as pending,
                           count(*) filter (where x.status = 'PENDING' and x.last_error is not null) as retrying
                    from ocr_search_outbox x join images i on i.id = x.image_id
                    where i.deleted_at is null
                )
                select total.total, states.indexed, states.pending, states.retrying
                from total cross join states
                """, (rs, row) -> new OcrSearchIndexStatusData(
                rs.getLong("total"), rs.getLong("indexed"), rs.getLong("pending"), rs.getLong("retrying")));
    }
}
