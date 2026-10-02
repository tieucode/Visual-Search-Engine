package com.visualsearch.repository;

import com.visualsearch.entity.BatchIndex;
import com.visualsearch.enums.BatchStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Repository
public interface BatchIndexRepository extends JpaRepository<BatchIndex, UUID> {

    /**
     * Tìm tất cả các batch có trạng thái cụ thể và không được cập nhật kể từ thời
     * điểm threshold (dùng để quét batch bị timeout).
     */
    List<BatchIndex> findByStatusAndUpdatedAtBefore(BatchStatus status, Instant threshold);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            update BatchIndex b
               set b.failedCount = b.failedCount + :count,
                   b.updatedAt = :updatedAt
             where b.id = :batchId
            """)
    int incrementFailed(
            @Param("batchId") UUID batchId,
            @Param("count") int count,
            @Param("updatedAt") Instant updatedAt);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = """
            with actual as (
                select count(*)\\:\\:int as cnt
                  from image_index
                 where batch_id = :batchId
            )
            update batch_index b
               set total_images = actual.cnt,
                   status       = case
                                      when b.success_count + b.failed_count >= actual.cnt then 'COMPLETED'
                                      else 'PROCESSING'
                                  end,
                   updated_at   = :updatedAt
              from actual
             where b.id = :batchId
               and b.status = 'UPLOADING'
            """, nativeQuery = true)
    int closeUploadPhase(@Param("batchId") UUID batchId, @Param("updatedAt") Instant updatedAt);
}
