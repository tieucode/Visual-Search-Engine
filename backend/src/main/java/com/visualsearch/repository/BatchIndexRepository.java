package com.visualsearch.repository;

import com.visualsearch.entity.BatchIndex;
import com.visualsearch.enums.BatchStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Repository
public interface BatchIndexRepository extends JpaRepository<BatchIndex, UUID> {

    /**
     * Tìm tất cả các batch có trạng thái cụ thể và không được cập nhật kể từ thời điểm threshold (dùng để quét batch bị timeout).
     */
    List<BatchIndex> findByStatusAndUpdatedAtBefore(BatchStatus status, Instant threshold);
}
