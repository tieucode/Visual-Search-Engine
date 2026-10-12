package com.visualsearch.controller;

import com.visualsearch.dto.BaseResponse;
import com.visualsearch.dto.search.SearchPageData;
import com.visualsearch.entity.User;
import com.visualsearch.service.search.ImageSearchService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

@RestController
@RequestMapping("/api/search/image")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Image search", description = "Search the shared image library with infinite scroll")
public class ImageSearchController {
    private final ImageSearchService service;

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Start a new image search", description = "Creates one history entry and returns the first page. "
            + "Use the returned searchId and nextCursor to load more results without creating another search.")
    public ResponseEntity<BaseResponse<SearchPageData>> start(
            @Parameter(required = true, description = "JPEG, PNG or WebP query image",
                    content = @Content(schema = @Schema(type = "string", format = "binary")))
            @RequestPart("file") MultipartFile file,
            @RequestParam(defaultValue = "20") int size,
            @AuthenticationPrincipal User user) {
        return ResponseEntity.status(HttpStatus.CREATED).body(BaseResponse.of(201, "Image search created",
                service.start(file, size, user)));
    }

    @GetMapping("/{searchId}/results")
    @Operation(summary = "Load the next page of an existing image search",
            description = "Send nextCursor unchanged. Reuses the stored query image and calls AI again. "
                    + "Never creates history. Only the user who started the search can load its pages.")
    public BaseResponse<SearchPageData> next(@PathVariable UUID searchId, @RequestParam String cursor,
                                             @AuthenticationPrincipal User user) {
        return BaseResponse.of(200, "Image search results retrieved", service.next(searchId, cursor, user));
    }
}
