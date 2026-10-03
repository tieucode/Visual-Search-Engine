package com.visualsearch.controller;

import com.visualsearch.dto.BaseResponse;
import com.visualsearch.dto.upload.ImageUploadRequest;
import com.visualsearch.dto.upload.ImageUploadData;
import com.visualsearch.entity.User;
import com.visualsearch.service.ImageUploadService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping({"/api/uploads/batches", "/api/batches"})
@RequiredArgsConstructor
@Tag(name = "Images", description = "Upload image chunks into a batch")
@SecurityRequirement(name = "bearerAuth")
public class ImageUploadController {

    private final ImageUploadService imageUploadService;

    @PostMapping(value = "/{batchId}/images", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(
            summary = "Upload up to 50 images to a batch",
            description = "Select one or more files in Swagger's files control. Set isLast=true only on the final upload request for the batch.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Upload result is in data", content = @Content(schema = @Schema(implementation = BaseResponse.class))),
            @ApiResponse(responseCode = "400", description = "Invalid upload request; error details are in data", content = @Content(schema = @Schema(implementation = BaseResponse.class))),
            @ApiResponse(responseCode = "401", description = "Missing, expired, or invalid access token; error details are in data", content = @Content(schema = @Schema(implementation = BaseResponse.class)))
    })
    public BaseResponse<ImageUploadData> uploadImages(
            @Parameter(description = "Batch ID returned by POST /api/uploads/batches/init", required = true)
            @PathVariable java.util.UUID batchId,
            @Parameter(description = "Marks this as the final upload request for the batch", example = "false")
            @RequestParam(defaultValue = "false") boolean isLast,
            @Parameter(
                    description = "One to fifty JPEG, PNG, or WebP files. Swagger displays a file picker for this field.",
                    required = true,
                    content = @Content(array = @ArraySchema(schema = @Schema(type = "string", format = "binary"))))
            @RequestPart("files") java.util.List<MultipartFile> files,
            @AuthenticationPrincipal User currentUser) {
        ImageUploadRequest request = ImageUploadRequest.builder()
                .batchId(batchId)
                .isLast(isLast)
                .files(files)
                .build();
        return BaseResponse.of(
                HttpStatus.OK.value(),
                "Images processed successfully",
                imageUploadService.processUploadBatch(request, currentUser));
    }
}
