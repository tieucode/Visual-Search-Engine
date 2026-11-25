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
                where i.deleted_at is null and i.type = 'UPLOAD' and i.media_kind = 'IMAGE'
                on conflict (image_id) do update set revision = ocr_search_outbox.revision + 1, status = 'PENDING', retry_count = 0,
                    last_error = null, next_attempt_at = now(), updated_at = now()
                """);
    }

    @Transactional(readOnly = true)
    public OcrSearchIndexStatusData status() {
        return jdbc.queryForObject("""
                with eligible as (
                    select i.id from images i
                    where i.deleted_at is null and i.type = 'UPLOAD' and i.media_kind = 'IMAGE'
                      and exists (select 1 from image_ocr o where o.image_id = i.id)
                )
                select count(*) as total,
                       count(*) filter (where x.status = 'INDEXED') as indexed,
                       count(*) filter (where x.status is distinct from 'INDEXED') as pending,
                       count(*) filter (where x.status in ('PROCESSING', 'PENDING') and x.last_error is not null) as retrying
                from eligible e left join ocr_search_outbox x on x.image_id = e.id
                """, (rs, row) -> new OcrSearchIndexStatusData(
                rs.getLong("total"), rs.getLong("indexed"), rs.getLong("pending"), rs.getLong("retrying")));
    }
}
