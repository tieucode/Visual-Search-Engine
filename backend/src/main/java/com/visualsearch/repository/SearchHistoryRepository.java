package com.visualsearch.repository;

import com.visualsearch.entity.SearchHistory;
import com.visualsearch.enums.SearchType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface SearchHistoryRepository extends JpaRepository<SearchHistory, UUID> {
    @EntityGraph(attributePaths = "queryImage")
    Optional<SearchHistory> findByIdAndUserIdAndSearchType(UUID id, UUID userId, SearchType searchType);

    @EntityGraph(attributePaths = "queryImage")
    @Query(value = "select h from SearchHistory h where h.user.id = :userId " +
            "and (:searchType is null or h.searchType = :searchType)",
            countQuery = "select count(h) from SearchHistory h where h.user.id = :userId " +
                    "and (:searchType is null or h.searchType = :searchType)")
    Page<SearchHistory> findHistoryForUser(@Param("userId") UUID userId,
                                           @Param("searchType") SearchType searchType,
                                           Pageable pageable);
}
