package com.visualsearch.indexing.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ImageIndexRepository extends JpaRepository<ImageIndexEntity, UUID> {

    Optional<ImageIndexEntity> findByImageId(UUID imageId);

    List<ImageIndexEntity> findByImageIdIn(Collection<UUID> imageIds);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            update ImageIndexEntity i
               set i.retryCount = case when i.retryCount < :attempt then :attempt else i.retryCount end,
                   i.updatedAt = :updatedAt
             where i.imageId = :imageId
               and i.status = com.visualsearch.indexing.persistence.IndexStatus.PENDING
            """)
    int markRetryStarted(@Param("imageId") UUID imageId,
                         @Param("attempt") int attempt,
                         @Param("updatedAt") Instant updatedAt);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            update ImageIndexEntity i
               set i.status = com.visualsearch.indexing.persistence.IndexStatus.SUCCESS,
                   i.errorMessage = null,
                   i.updatedAt = :updatedAt
             where i.imageId = :imageId
               and i.status = com.visualsearch.indexing.persistence.IndexStatus.PENDING
            """)
    int markSuccess(@Param("imageId") UUID imageId, @Param("updatedAt") Instant updatedAt);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            update ImageIndexEntity i
               set i.status = com.visualsearch.indexing.persistence.IndexStatus.FAILED,
                   i.errorMessage = :errorMessage,
                   i.updatedAt = :updatedAt
             where i.imageId = :imageId
               and i.status = com.visualsearch.indexing.persistence.IndexStatus.PENDING
            """)
    int markPermanentImageFailure(@Param("imageId") UUID imageId,
                                  @Param("errorMessage") String errorMessage,
                                  @Param("updatedAt") Instant updatedAt);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            update ImageIndexEntity i
               set i.status = com.visualsearch.indexing.persistence.IndexStatus.FAILED,
                   i.retryCount = case when i.retryCount < :attempt then :attempt else i.retryCount end,
                   i.errorMessage = :errorMessage,
                   i.updatedAt = :updatedAt
             where i.imageId = :imageId
               and i.status = com.visualsearch.indexing.persistence.IndexStatus.PENDING
            """)
    int markPermanentFailure(@Param("imageId") UUID imageId,
                             @Param("attempt") int attempt,
                             @Param("errorMessage") String errorMessage,
                             @Param("updatedAt") Instant updatedAt);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            update ImageIndexEntity i
               set i.status = com.visualsearch.indexing.persistence.IndexStatus.FAILED,
                   i.retryCount = :attempt,
                   i.errorMessage = :errorMessage,
                   i.updatedAt = :updatedAt
             where i.imageId = :imageId
               and i.status = com.visualsearch.indexing.persistence.IndexStatus.PENDING
            """)
    int markRetriesExhausted(@Param("imageId") UUID imageId,
                             @Param("attempt") int attempt,
                             @Param("errorMessage") String errorMessage,
                             @Param("updatedAt") Instant updatedAt);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            update ImageIndexEntity i
               set i.errorMessage = :errorMessage,
                   i.updatedAt = :updatedAt
             where i.imageId = :imageId
               and i.status = com.visualsearch.indexing.persistence.IndexStatus.PENDING
            """)
    int recordTransientFailure(@Param("imageId") UUID imageId,
                               @Param("errorMessage") String errorMessage,
                               @Param("updatedAt") Instant updatedAt);
}
