package com.visualsearch.controller;

import com.visualsearch.dto.BaseResponse;
import com.visualsearch.dto.auth.AuthData;
import com.visualsearch.dto.auth.LoginRequest;
import com.visualsearch.dto.auth.RegisterRequest;
import com.visualsearch.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Tag(name = "Authentication", description = "Register and sign in with a 24-hour JWT access token. Refresh tokens are not used.")
public class AuthController {

    private final AuthService authService;

    @PostMapping("/register")
    @Operation(
            summary = "Register a new account",
            description = "Creates a USER account and immediately returns one JWT access token.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Account created; authentication payload is in data", content = @Content(schema = @Schema(implementation = BaseResponse.class))),
            @ApiResponse(responseCode = "400", description = "Invalid request body; error details are in data", content = @Content(schema = @Schema(implementation = BaseResponse.class))),
            @ApiResponse(responseCode = "409", description = "Email is already registered; error details are in data", content = @Content(schema = @Schema(implementation = BaseResponse.class)))
    })
    public ResponseEntity<BaseResponse<AuthData>> register(@Valid @RequestBody RegisterRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(BaseResponse.of(HttpStatus.CREATED.value(), "Account created successfully", authService.register(request)));
    }

    @PostMapping("/login")
    @Operation(
            summary = "Sign in",
            description = "Validates credentials and returns one JWT access token. No refresh token is issued.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Signed in; authentication payload is in data", content = @Content(schema = @Schema(implementation = BaseResponse.class))),
            @ApiResponse(responseCode = "400", description = "Invalid request body; error details are in data", content = @Content(schema = @Schema(implementation = BaseResponse.class))),
            @ApiResponse(responseCode = "401", description = "Invalid email or password; error details are in data", content = @Content(schema = @Schema(implementation = BaseResponse.class)))
    })
    public BaseResponse<AuthData> login(@Valid @RequestBody LoginRequest request) {
        return BaseResponse.of(HttpStatus.OK.value(), "Signed in successfully", authService.login(request));
    }
}
