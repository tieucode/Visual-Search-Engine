package com.visualsearch.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/** Standard envelope returned by every API endpoint. */
@Getter
@Setter
@NoArgsConstructor
@Schema(description = "Standard API response envelope")
public class BaseResponse<T> {

    @Schema(description = "HTTP status code", example = "200")
    private int status;

    @Schema(description = "Human-readable result message", example = "Request completed successfully")
    private String message;

    @Schema(description = "UTC instant at which the response was generated", example = "2026-09-26T03:00:00Z")
    private Instant timestamp;

    @Schema(description = "Response payload")
    private T data;

    public BaseResponse(int status, String message, Instant timestamp, T data) {
        this.status = status;
        this.message = message;
        this.timestamp = timestamp;
        this.data = data;
    }

    public static <T> BaseResponse<T> of(int status, String message, T data) {
        return new BaseResponse<>(status, message, Instant.now(), data);
    }
}
