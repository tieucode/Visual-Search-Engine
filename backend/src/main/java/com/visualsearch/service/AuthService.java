package com.visualsearch.service;

import com.visualsearch.config.JwtProperties;
import com.visualsearch.dto.auth.AuthData;
import com.visualsearch.dto.auth.AuthenticatedUserData;
import com.visualsearch.dto.auth.LoginRequest;
import com.visualsearch.dto.auth.RegisterRequest;
import com.visualsearch.entity.User;
import com.visualsearch.enums.Role;
import com.visualsearch.exception.ConflictException;
import com.visualsearch.repository.UserRepository;
import com.visualsearch.security.JwtService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final JwtProperties jwtProperties;

    @Transactional
    public AuthData register(RegisterRequest request) {
        String email = normalizeEmail(request.getEmail());
        if (userRepository.existsByEmail(email)) {
            throw new ConflictException("An account with this email already exists");
        }

        User user = User.builder()
                .name(request.getName().trim())
                .email(email)
                .password(passwordEncoder.encode(request.getPassword()))
                .role(Role.USER)
                .build();

        return createAuthData(userRepository.save(user));
    }

    @Transactional(readOnly = true)
    public AuthData login(LoginRequest request) {
        User user = userRepository.findByEmailAndDeletedAtIsNull(normalizeEmail(request.getEmail()))
                .orElseThrow(() -> new BadCredentialsException("Invalid email or password"));

        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new BadCredentialsException("Invalid email or password");
        }

        return createAuthData(user);
    }

    private AuthData createAuthData(User user) {
        Instant issuedAt = Instant.now();
        long expirationMs = jwtProperties.getExpirationMs();
        return AuthData.builder()
                .accessToken(jwtService.generateAccessToken(user, issuedAt))
                .expiresIn(expirationMs / 1000)
                .expiresAt(issuedAt.plusMillis(expirationMs))
                .user(AuthenticatedUserData.builder()
                        .id(user.getId())
                        .email(user.getEmail())
                        .name(user.getName())
                        .role(user.getRole())
                        .build())
                .build();
    }

    private String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
