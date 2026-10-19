package com.visualsearch.dto.search;

import com.visualsearch.enums.SearchType;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.UUID;

<<<<<<< HEAD
public record SearchPageData<T>(UUID searchId, SearchType searchType, List<T> items,
                             int size, boolean hasMore, String nextCursor) {}
=======
@Schema(description = "Cursor-paginated search results; no total count or page number")
public record SearchPageData(
        @Schema(description = "Search history ID, reused for every continuation", format = "uuid",
                requiredMode = Schema.RequiredMode.REQUIRED)
        UUID searchId,
        @Schema(description = "Search mode: IMAGE_ONLY for image queries, TEXT_SEMANTIC for descriptions",
                requiredMode = Schema.RequiredMode.REQUIRED)
        SearchType searchType,
        @Schema(description = "Images in descending similarity order; may be empty",
                requiredMode = Schema.RequiredMode.REQUIRED)
        List<SearchImageData> items,
        @Schema(description = "Requested page size; items may contain fewer images", minimum = "1", maximum = "100",
                example = "40", requiredMode = Schema.RequiredMode.REQUIRED)
        int size,
        @Schema(description = "Whether a further page is available", requiredMode = Schema.RequiredMode.REQUIRED)
        boolean hasMore,
        @Schema(description = "Signed opaque continuation token; null when hasMore is false",
                nullable = true, requiredMode = Schema.RequiredMode.REQUIRED)
        String nextCursor) {}
>>>>>>> 148ac47b8fa6eaedd855324301e9d9e9cf893b69
