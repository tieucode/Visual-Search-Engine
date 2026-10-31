package com.visualsearch.indexing.persistence;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(transactionManager = "indexingTransactionManager")
public class IndexingStateService {
    private final IndexingJdbcRepository repository;

    @Transactional(transactionManager = "indexingTransactionManager", readOnly = true)
    public Map<UUID, ImageState> findByImageIds(Collection<UUID> ids) {
        return repository.findByImageIds(ids);
    }

    public void markRetryStarted(Collection<UUID> ids, int attempt) {
        if (attempt > 0 && !ids.isEmpty()) repository.markRetryStarted(ids, attempt);
    }

    public Map<UUID, IndexStatus> recordOutcomes(Collection<IndexWrite> outcomes) {
        return outcomes.isEmpty() ? Map.of() : repository.recordOutcomes(outcomes);
    }
}
