package com.visualsearch.dto.auth;

import com.visualsearch.enums.Role;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Public account information")
public class AuthenticatedUserData {

    @Schema(example = "e36f700d-1e5d-4c46-9bfb-47db7085a197")
    private UUID id;

    @Schema(example = "nguyenvana@example.com")
    private String email;

    @Schema(example = "Nguyen Van A")
    private String name;

    @Schema(example = "USER")
    private Role role;
}
