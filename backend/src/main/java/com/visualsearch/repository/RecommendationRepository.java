package com.visualsearch.repository;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class RecommendationRepository {
    private final NamedParameterJdbcTemplate jdbc;

    public List<UUID> findPopularOrRecent(Collection<UUID> excluded, int limit) {
        String sql = """
                with popular as (
                    select clicked_image_id, count(distinct user_id) as viewers
                    from search_clicks
                    where created_at >= now() - interval '7 days' and user_id is not null
                    group by clicked_image_id
                )
                select i.id from images i
                join image_index ix on ix.image_id = i.id and ix.status = 'SUCCESS'
                left join popular p on p.clicked_image_id = i.id
                where i.type = 'UPLOAD' and i.deleted_at is null
                """;
        var params = new MapSqlParameterSource("limit", limit);
        if (!excluded.isEmpty()) {
            sql += " and i.id not in (:excluded)";
            params.addValue("excluded", excluded);
        }
        return jdbc.query(sql + " order by p.viewers desc nulls last, i.created_at desc, i.id desc limit :limit",
                params, (rs, row) -> rs.getObject("id", UUID.class));
    }
}
