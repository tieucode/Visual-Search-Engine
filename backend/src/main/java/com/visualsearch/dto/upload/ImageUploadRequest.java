package com.visualsearch.dto.upload;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ImageUploadRequest {

    @NotNull(message = "batchId is required")
    private UUID batchId;

    @Builder.Default
    private boolean isLast = false;

    @NotEmpty(message = "files cannot be empty")
    @Size(min = 1, max = 50, message = "files must be between 1 and 50")
    private List<MultipartFile> files;
}
