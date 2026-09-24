package com.visualsearch.repository;

import com.visualsearch.entity.ImageIndex;
import com.visualsearch.enums.IndexStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ImageIndexRepository extends JpaRepository<ImageIndex, UUID> {

    int countByBatchId(UUID batchId);

    int countByBatchIdAndStatus(UUID batchId, IndexStatus status);

    Optional<ImageIndex> findByImageId(UUID imageId);

    List<ImageIndex> findByBatchId(UUID batchId);

}
