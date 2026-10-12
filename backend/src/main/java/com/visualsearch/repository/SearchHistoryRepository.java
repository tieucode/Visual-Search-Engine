package com.visualsearch.repository;

import com.visualsearch.entity.SearchHistory;
import com.visualsearch.enums.SearchType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface SearchHistoryRepository extends JpaRepository<SearchHistory, UUID> {
    @EntityGraph(attributePaths = "queryImage")
    Optional<SearchHistory> findByIdAndUserIdAndSearchType(UUID id, UUID userId, SearchType searchType);
}
