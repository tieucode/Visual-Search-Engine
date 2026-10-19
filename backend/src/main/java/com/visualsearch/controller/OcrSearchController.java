package com.visualsearch.controller;

import com.visualsearch.dto.BaseResponse;
import com.visualsearch.dto.search.SearchPageData;
import com.visualsearch.dto.search.OcrSearchImageData;
import com.visualsearch.entity.User;
import com.visualsearch.service.search.OcrSearchService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/search/ocr")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "OCR search", description = "Search images by text recognized in their contents")
public class OcrSearchController {
    private final OcrSearchService service;

    @GetMapping
    @Operation(summary = "Start a new OCR search",
            description = "Creates one history entry and returns the first page of matches.")
    public ResponseEntity<BaseResponse<SearchPageData<OcrSearchImageData>>> start(
            @RequestParam("q") String keyword,
            @RequestParam(defaultValue = "20") int size,
            @AuthenticationPrincipal User user) {
        return ResponseEntity.status(HttpStatus.CREATED).body(BaseResponse.of(201, "OCR search created",
                service.start(keyword, size, user)));
    }

    @GetMapping("/{searchId}/results")
    @Operation(summary = "Load the next page of an existing OCR search",
            description = "Uses the original normalized query and does not create another history entry.")
    public BaseResponse<SearchPageData<OcrSearchImageData>> next(@PathVariable UUID searchId, @RequestParam String cursor,
                                             @AuthenticationPrincipal User user) {
        return BaseResponse.of(200, "OCR search results retrieved", service.next(searchId, cursor, user));
    }
}
