package com.visualsearch.controller;

import com.visualsearch.dto.upload.BatchInitRequest;
import com.visualsearch.dto.upload.BatchInitResponse;
import com.visualsearch.dto.upload.BatchStatusResponse;
import com.visualsearch.entity.User;
import com.visualsearch.service.BatchService;
import io.swagger.v3.oas.annotations.Operation;
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
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

/** REST endpoints for creating and monitoring image-upload batches. */
@RestController
@RequestMapping("/api/batches")
@RequiredArgsConstructor
@Tag(name = "Batches", description = "Create and monitor image upload batches")
public class BatchController {

    private final BatchService batchService;

    @PostMapping("/init")
    @Operation(summary = "Initialize an upload batch")
    public ResponseEntity<BatchInitResponse> initBatch(
            @Valid @RequestBody BatchInitRequest request,
            @AuthenticationPrincipal User currentUser) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(batchService.initBatch(request, requireAuthenticatedUser(currentUser)));
    }

    @GetMapping("/{batchId}")
    @Operation(summary = "Get batch processing status")
    public BatchStatusResponse getBatchStatus(@PathVariable UUID batchId) {
        return batchService.getBatchStatus(batchId);
    }

    private User requireAuthenticatedUser(User currentUser) {
        if (currentUser == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication is required");
        }
        return currentUser;
    }
}
