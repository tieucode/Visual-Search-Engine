package com.visualsearch.controller;

import com.visualsearch.dto.BaseResponse;
import com.visualsearch.dto.upload.BatchInitData;
import com.visualsearch.dto.upload.BatchInitRequest;
import com.visualsearch.dto.upload.BatchPageData;
import com.visualsearch.dto.upload.BatchStatusData;
import com.visualsearch.entity.User;
import com.visualsearch.service.BatchService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/** REST endpoints for creating and monitoring image-upload batches. */
@RestController
@RequestMapping({"/api/uploads/batches", "/api/batches"})
@RequiredArgsConstructor
@Tag(name = "Batches", description = "Create and monitor image upload batches")
@SecurityRequirement(name = "bearerAuth")
public class BatchController {

    private final BatchService batchService;

    @PostMapping("/init")
    @Operation(summary = "Initialize an upload batch")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Batch initialized; batch data is in data", content = @Content(schema = @Schema(implementation = BaseResponse.class))),
            @ApiResponse(responseCode = "400", description = "Invalid request; error details are in data", content = @Content(schema = @Schema(implementation = BaseResponse.class))),
            @ApiResponse(responseCode = "401", description = "Missing, expired, or invalid access token; error details are in data", content = @Content(schema = @Schema(implementation = BaseResponse.class)))
    })
    public ResponseEntity<BaseResponse<BatchInitData>> initBatch(
            @Valid @RequestBody BatchInitRequest request,
            @AuthenticationPrincipal User currentUser) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(BaseResponse.of(
                        HttpStatus.CREATED.value(),
                        "Batch initialized successfully",
                        batchService.initBatch(request, currentUser)));
    }

    @GetMapping
    @Operation(summary = "List batches for the signed-in user with client-selected pagination")
    public BaseResponse<BatchPageData> listBatches(
            @RequestParam(defaultValue = "PROCESSING") String status,
            @RequestParam(defaultValue = "createdAt,desc") String sort,
            @RequestParam int page,
            @RequestParam int size,
            @AuthenticationPrincipal User currentUser) {
        return BaseResponse.of(
                HttpStatus.OK.value(),
                "Batches retrieved successfully",
                batchService.listBatches(currentUser, status, sort, page, size));
    }

    @GetMapping("/{batchId}")
    @Operation(summary = "Get batch processing status")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Batch status is in data", content = @Content(schema = @Schema(implementation = BaseResponse.class))),
            @ApiResponse(responseCode = "401", description = "Missing, expired, or invalid access token; error details are in data", content = @Content(schema = @Schema(implementation = BaseResponse.class))),
            @ApiResponse(responseCode = "404", description = "Batch not found; error details are in data", content = @Content(schema = @Schema(implementation = BaseResponse.class)))
    })
    public BaseResponse<BatchStatusData> getBatchStatus(
            @PathVariable UUID batchId,
            @AuthenticationPrincipal User currentUser) {
        return BaseResponse.of(
                HttpStatus.OK.value(),
                "Batch status retrieved successfully",
                batchService.getBatchStatus(batchId, currentUser));
    }
}
