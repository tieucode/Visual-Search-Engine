package com.visualsearch.controller;

import com.visualsearch.dto.BaseResponse;
import com.visualsearch.dto.history.SearchHistoryPageData;
import com.visualsearch.entity.User;
import com.visualsearch.enums.SearchType;
import com.visualsearch.service.search.SearchHistoryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/search/history")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Search history", description = "Browse search history belonging to the authenticated user")
public class SearchHistoryController {

    private final SearchHistoryService service;

    @GetMapping
    @Operation(summary = "Get my search history",
            description = "Returns this user's search_history records, newest first. " +
                    "Each successful new search creates one record; requesting another results page does not.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "History page retrieved",
                            content = @Content(schema = @Schema(implementation = SearchHistoryPageResponse.class))),
                    @ApiResponse(responseCode = "400", description = "Invalid page, size, or search type"),
                    @ApiResponse(responseCode = "401", description = "A valid access token is required")
            })
    public BaseResponse<SearchHistoryPageData> getMyHistory(
            @Parameter(description = "Zero-based page index", example = "0", in = ParameterIn.QUERY)
            @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Number of records per page (1–100)", example = "20", in = ParameterIn.QUERY)
            @RequestParam(defaultValue = "20") int size,
            @Parameter(description = "Optional filter: IMAGE_ONLY, TEXT_SEMANTIC, or TEXT_OCR",
                    schema = @Schema(allowableValues = {"IMAGE_ONLY", "TEXT_SEMANTIC", "TEXT_OCR"}),
                    in = ParameterIn.QUERY)
            @RequestParam(required = false) SearchType searchType,
            @AuthenticationPrincipal User user) {
        return BaseResponse.of(HttpStatus.OK.value(), "Search history retrieved",
                service.getHistory(user, searchType, page, size));
    }

    /** OpenAPI schema helper so Swagger UI documents the response envelope clearly. */
    @Schema(name = "SearchHistoryPageResponse", description = "Paginated search history response")
    private static class SearchHistoryPageResponse {
        @Schema(description = "HTTP status code", example = "200") public int status;
        @Schema(description = "Response message", example = "Search history retrieved") public String message;
        @Schema(description = "UTC response timestamp", example = "2026-10-19T11:03:44Z") public String timestamp;
        @Schema(description = "History page payload") public SearchHistoryPageData data;
    }
}
