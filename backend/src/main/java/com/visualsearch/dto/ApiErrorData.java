package com.visualsearch.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Standard API error response")
public class ApiErrorData {

    @Schema(example = "Unauthorized")
    private String error;

    @Schema(example = "/api/auth/login")
    private String path;

    @Schema(description = "Validation errors keyed by field name")
    private Map<String, String> fieldErrors;
}
