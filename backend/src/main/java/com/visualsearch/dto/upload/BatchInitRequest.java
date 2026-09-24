package com.visualsearch.dto.upload;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BatchInitRequest {

    @NotNull(message = "totalImages is required")
    @Min(value = 1, message = "totalImages must be at least 1")
    @Max(value = 1000, message = "totalImages must be at most 1000")
    private Integer totalImages;
}
