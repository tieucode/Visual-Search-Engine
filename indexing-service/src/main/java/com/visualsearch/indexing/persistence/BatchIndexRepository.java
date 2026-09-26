package com.visualsearch.indexing.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.UUID;

public interface BatchIndexRepository extends JpaRepository<BatchIndexEntity, UUID> {

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            update BatchIndexEntity b
               set b.successCount = b.successCount + 1,
                   b.updatedAt = :updatedAt
             where b.id = :batchId
            """)
    int incrementSuccess(@Param("batchId") UUID batchId, @Param("updatedAt") Instant updatedAt);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            update BatchIndexEntity b
               set b.failedCount = b.failedCount + 1,
                   b.updatedAt = :updatedAt
             where b.id = :batchId
            """)
    int incrementFailed(@Param("batchId") UUID batchId, @Param("updatedAt") Instant updatedAt);
}
