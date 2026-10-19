package com.visualsearch.indexing.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;

import java.util.UUID;

public interface ImageOcrRepository extends JpaRepository<ImageOcrEntity, UUID> {

    @Modifying(flushAutomatically = true)
    void deleteByImageId(UUID imageId);
}
