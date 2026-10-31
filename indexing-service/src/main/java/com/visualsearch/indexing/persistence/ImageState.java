package com.visualsearch.indexing.persistence;

import java.util.UUID;

public record ImageState(UUID imageId, UUID batchId, IndexStatus status) {}
