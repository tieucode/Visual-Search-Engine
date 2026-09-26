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
@Schema(description = "Information required to create an account")
public class RegisterRequest {

    @NotBlank(message = "name is required")
    @Size(min = 2, max = 50, message = "name must be between 2 and 50 characters")
    @Schema(example = "Nguyen Van A", requiredMode = Schema.RequiredMode.REQUIRED)
    private String name;

    @NotBlank(message = "email is required")
    @Email(message = "email must be valid")
    @Size(max = 50, message = "email must not exceed 50 characters")
    @Schema(example = "nguyenvana@example.com", requiredMode = Schema.RequiredMode.REQUIRED)
    private String email;

    @NotBlank(message = "password is required")
    @Size(min = 8, max = 72, message = "password must be between 8 and 72 characters")
    @Schema(example = "strong-password-123", requiredMode = Schema.RequiredMode.REQUIRED, format = "password")
    private String password;
}
