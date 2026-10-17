package com.visualsearch.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/** Concrete error envelope for OpenAPI's generic payload resolution. */
@Schema(description = "API error envelope with structured error details in data")
public class ApiErrorResponse extends BaseResponse<ApiErrorData> {}
