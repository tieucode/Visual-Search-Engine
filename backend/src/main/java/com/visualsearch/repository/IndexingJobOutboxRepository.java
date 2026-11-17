package com.visualsearch.repository;

import com.visualsearch.entity.IndexingJobOutbox;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface IndexingJobOutboxRepository extends JpaRepository<IndexingJobOutbox, UUID> {}
