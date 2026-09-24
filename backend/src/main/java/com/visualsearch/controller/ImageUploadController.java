package com.visualsearch.controller;

import com.visualsearch.dto.upload.ImageUploadRequest;
import com.visualsearch.dto.upload.ImageUploadResponse;
import com.visualsearch.entity.User;
import com.visualsearch.service.ImageUploadService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/images")
@RequiredArgsConstructor
@Tag(name = "Images", description = "Upload image chunks into a batch")
public class ImageUploadController {

    private final ImageUploadService imageUploadService;

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Upload up to 20 images to a batch")
    public ImageUploadResponse uploadImages(
            @Valid @ModelAttribute ImageUploadRequest request,
            @AuthenticationPrincipal User currentUser) {
        return imageUploadService.processUploadBatch(request, requireAuthenticatedUser(currentUser));
    }

    private User requireAuthenticatedUser(User currentUser) {
        if (currentUser == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication is required");
        }
        return currentUser;
    }
}
