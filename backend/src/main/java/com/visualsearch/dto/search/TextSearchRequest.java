package com.visualsearch.dto.search;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record TextSearchRequest(
        @NotBlank(message = "text must not be blank")
        @Size(max = 500, message = "text must not exceed 500 characters")
        @Schema(description = "Image description; leading and trailing whitespace is removed",
                example = "A small house beside a lake", minLength = 1, maxLength = 500,
                requiredMode = Schema.RequiredMode.REQUIRED)
        String text) {
    public TextSearchRequest {
        text = text == null ? null : text.strip();
    }
}
