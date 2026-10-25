package com.visualsearch.dto.search;

import com.visualsearch.enums.SearchType;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.UUID;

@Schema(description = "Cursor-paginated search results; no total count or page number")
public record SearchPageData<T>(
        @Schema(description = "Search history ID, reused for every continuation", format = "uuid",
                requiredMode = Schema.RequiredMode.REQUIRED)
        UUID searchId,
        @Schema(description = "Search mode: IMAGE_ONLY for image queries, TEXT_SEMANTIC for descriptions",
                requiredMode = Schema.RequiredMode.REQUIRED)
        SearchType searchType,
        @Schema(description = "Images in descending similarity order; may be empty",
                requiredMode = Schema.RequiredMode.REQUIRED)
        List<T> items,
        @Schema(description = "Requested page size; items may contain fewer images", minimum = "1", maximum = "100",
                example = "40", requiredMode = Schema.RequiredMode.REQUIRED)
        int size,
        @Schema(description = "Whether a further page is available", requiredMode = Schema.RequiredMode.REQUIRED)
        boolean hasMore,
        @Schema(description = "Signed opaque continuation token; null when hasMore is false",
                nullable = true, requiredMode = Schema.RequiredMode.REQUIRED)
        String nextCursor) {}
