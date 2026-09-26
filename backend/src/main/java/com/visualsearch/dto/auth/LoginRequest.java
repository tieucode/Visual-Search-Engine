package com.visualsearch.dto.auth;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Credentials for signing in")
public class LoginRequest {

    @NotBlank(message = "email is required")
    @Email(message = "email must be valid")
    @Size(max = 50, message = "email must not exceed 50 characters")
    @Schema(example = "nguyenvana@example.com", requiredMode = Schema.RequiredMode.REQUIRED)
    private String email;

    @NotBlank(message = "password is required")
    @Size(max = 72, message = "password must not exceed 72 characters")
    @Schema(example = "strong-password-123", requiredMode = Schema.RequiredMode.REQUIRED, format = "password")
    private String password;
}
