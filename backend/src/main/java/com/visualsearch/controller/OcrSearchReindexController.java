package com.visualsearch.controller;

import com.visualsearch.dto.BaseResponse;
import com.visualsearch.dto.admin.OcrSearchIndexStatusData;
import com.visualsearch.service.OcrSearchReindexService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/ocr-search/reindex")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@PreAuthorize("hasRole('ADMIN')")
public class OcrSearchReindexController {
    private final OcrSearchReindexService service;

    @PostMapping
    @Operation(summary = "Queue OCR documents for Elasticsearch reindexing")
    public ResponseEntity<BaseResponse<OcrSearchIndexStatusData>> reindex() {
        service.reindexAll();
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(
                BaseResponse.of(202, "OCR reindexing accepted", service.status()));
    }

    @GetMapping
    @Operation(summary = "Get OCR Elasticsearch indexing progress")
    public BaseResponse<OcrSearchIndexStatusData> status() {
        return BaseResponse.of(200, "OCR indexing status retrieved", service.status());
    }
}
