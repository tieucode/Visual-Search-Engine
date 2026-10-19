package com.visualsearch.controller;

import com.visualsearch.dto.ApiErrorResponse;
import com.visualsearch.dto.BaseResponse;
import com.visualsearch.service.search.SearchPaginationService;
import com.visualsearch.dto.search.SearchPageData;
import com.visualsearch.dto.search.TextSearchRequest;
import com.visualsearch.entity.User;
import com.visualsearch.service.search.TextSearchService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping(value = "/api/search/text", produces = MediaType.APPLICATION_JSON_VALUE)
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Text search", description = "Search the shared image library by description with infinite scroll")
@ApiResponses({
        @ApiResponse(responseCode = "400", description = "Invalid text, page size, JSON body or cursor",
                content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
        @ApiResponse(responseCode = "401", description = "A valid access token is required",
                content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
        @ApiResponse(responseCode = "502", description = "AI, Qdrant or image storage failed",
                content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
})
public class TextSearchController {
    private final TextSearchService textSearch;

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Start a new semantic text search",
            description = "Embeds the description and queries Qdrant. Creates one TEXT_SEMANTIC history entry. "
                    + "Use searchId and nextCursor to load more results without resubmitting the text.")
    @ApiResponse(responseCode = "201", description = "Search created; the first result page is in data",
            useReturnTypeSchema = true)
    public BaseResponse<SearchPageData> start(
            @Valid @RequestBody TextSearchRequest request,
            @Parameter(description = "Results per page; preserved in the cursor",
                    schema = @Schema(minimum = "1", maximum = "100", defaultValue = SearchPaginationService.DEFAULT_PAGE_SIZE))
            @RequestParam(defaultValue = SearchPaginationService.DEFAULT_PAGE_SIZE) int size,
            @Parameter(hidden = true) @AuthenticationPrincipal User user) {
        return BaseResponse.of(201, "Text search created", textSearch.start(request, size, user));
    }

    @GetMapping("/{searchId}/results")
    @Operation(summary = "Load the next page of an existing text search",
            description = "Send nextCursor unchanged. Reuses the stored description and calls AI again. "
                    + "Never creates history. Only the user who started the search can load its pages.")
    @ApiResponse(responseCode = "200", description = "Result page in data; nextCursor is null at the end",
            useReturnTypeSchema = true)
    @ApiResponse(responseCode = "404", description = "Search not found, belongs to another user, has another type, or query text is missing",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    public BaseResponse<SearchPageData> next(
            @PathVariable UUID searchId,
            @Parameter(description = "Opaque nextCursor from the previous page; send unchanged")
            @RequestParam String cursor,
            @Parameter(hidden = true) @AuthenticationPrincipal User user) {
        return BaseResponse.of(200, "Text search results retrieved", textSearch.next(searchId, cursor, user));
    }
}
