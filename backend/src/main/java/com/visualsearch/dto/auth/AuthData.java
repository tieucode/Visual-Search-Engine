package com.visualsearch.dto.auth;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Successful authentication result. Only an access token is issued.")
public class AuthData {

    @Schema(description = "JWT access token. Send it as Authorization: Bearer <accessToken>.", example = "eyJhbGciOiJIUzI1NiJ9...")
    private String accessToken;

    @Builder.Default
    @Schema(example = "Bearer")
    private String tokenType = "Bearer";

    @Schema(description = "Access-token lifetime in seconds", example = "86400")
    private long expiresIn;

    @Schema(description = "UTC instant at which the access token expires", example = "2026-09-27T03:00:00Z")
    private Instant expiresAt;

    private AuthenticatedUserData user;
}
