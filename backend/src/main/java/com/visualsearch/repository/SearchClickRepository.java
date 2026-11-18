package com.visualsearch.repository;

import com.visualsearch.entity.SearchClick;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.UUID;

public interface SearchClickRepository extends JpaRepository<SearchClick, UUID> {
    @Query(value = """
            select c.clicked_image_id
            from search_clicks c join images i on i.id = c.clicked_image_id
            where c.user_id = :userId and i.type = 'UPLOAD' and i.deleted_at is null
            group by c.clicked_image_id
            order by max(c.created_at) desc, c.clicked_image_id desc
            limit 100
            """, nativeQuery = true)
    List<UUID> findRecentImageIds(UUID userId);
}
