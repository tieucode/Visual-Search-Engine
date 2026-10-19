package com.visualsearch.dto.history;

import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "A page of search history records")
public record SearchHistoryPageData(
        @ArraySchema(schema = @Schema(implementation = SearchHistoryData.class))
        List<SearchHistoryData> items,
        @Schema(example = "0") int page,
        @Schema(example = "20") int size,
        @Schema(example = "37") long totalElements,
        @Schema(example = "2") int totalPages,
        @Schema(example = "true") boolean hasNext,
        @Schema(example = "false") boolean hasPrevious
) {
}
