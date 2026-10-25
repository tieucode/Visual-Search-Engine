package com.visualsearch.controller;

import com.visualsearch.dto.ApiErrorResponse;
import com.visualsearch.dto.BaseResponse;
import com.visualsearch.dto.search.SearchPageData;
import com.visualsearch.dto.search.SearchImageData;
import com.visualsearch.entity.User;
import com.visualsearch.service.search.ImageSearchService;
import com.visualsearch.service.search.SearchPaginationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

@RestController
@RequestMapping(value = "/api/search/image", produces = MediaType.APPLICATION_JSON_VALUE)
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Image search", description = "Search the shared image library with infinite scroll")
@ApiResponses({
        @ApiResponse(responseCode = "400", description = "Invalid image, page size or cursor",
                content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
        @ApiResponse(responseCode = "401", description = "A valid access token is required",
                content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
        @ApiResponse(responseCode = "502", description = "AI, Qdrant or image storage failed",
                content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
})
public class ImageSearchController {
    private final ImageSearchService imageSearch;

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Start a new image search", description = "Creates one history entry and returns the first page. "
            + "Use the returned searchId and nextCursor to load more results without creating another search.")
    @ApiResponse(responseCode = "201", description = "Search created; the first result page is in data",
            useReturnTypeSchema = true)
    @ApiResponse(responseCode = "413", description = "Multipart upload exceeds the configured size limit",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    public BaseResponse<SearchPageData<SearchImageData>> start(
            @Parameter(required = true, description = "JPEG, PNG or WebP query image",
                    content = @Content(schema = @Schema(type = "string", format = "binary")))
            @RequestPart("file") MultipartFile file,
            @Parameter(description = "Results per page; preserved in the cursor",
                    schema = @Schema(minimum = "1", maximum = "100", defaultValue = SearchPaginationService.DEFAULT_PAGE_SIZE))
            @RequestParam(defaultValue = SearchPaginationService.DEFAULT_PAGE_SIZE) int size,
            @Parameter(hidden = true) @AuthenticationPrincipal User user) {
        return BaseResponse.of(201, "Image search created", imageSearch.start(file, size, user));
    }

    @PostMapping("/{imageId}/similar")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Find similar images using an indexed library image",
            description = "Uses the existing Qdrant vector, without downloading the image. Returns a search session with cursor pagination.")
    @ApiResponse(responseCode = "201", description = "Similarity search created", useReturnTypeSchema = true)
    @ApiResponse(responseCode = "404", description = "Library image not found",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    public BaseResponse<SearchPageData<SearchImageData>> similar(
            @PathVariable UUID imageId,
            @Parameter(description = "Results per page; preserved in the cursor",
                    schema = @Schema(minimum = "1", maximum = "100", defaultValue = SearchPaginationService.DEFAULT_PAGE_SIZE))
            @RequestParam(defaultValue = SearchPaginationService.DEFAULT_PAGE_SIZE) int size,
            @Parameter(hidden = true) @AuthenticationPrincipal User user) {
        return BaseResponse.of(201, "Similarity search created", imageSearch.startSimilar(imageId, size, user));
    }

    @GetMapping("/{searchId}/results")
    @Operation(summary = "Load the next page of an existing image search",
            description = "Send nextCursor unchanged. Uploaded queries call AI again; library-image queries reuse their Qdrant vector. "
                    + "Never creates history. Only the user who started the search can load its pages.")
    @ApiResponse(responseCode = "200", description = "Result page in data; nextCursor is null at the end",
            useReturnTypeSchema = true)
    @ApiResponse(responseCode = "404", description = "Search not found, belongs to another user, has another type, or query image is missing",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    public BaseResponse<SearchPageData<SearchImageData>> next(
            @PathVariable UUID searchId,
            @Parameter(description = "Opaque nextCursor from the previous page; send unchanged")
            @RequestParam String cursor,
            @Parameter(hidden = true) @AuthenticationPrincipal User user) {
        return BaseResponse.of(200, "Image search results retrieved", imageSearch.next(searchId, cursor, user));
    }
}
