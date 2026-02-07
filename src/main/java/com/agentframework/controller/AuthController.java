package com.agentframework.controller;

import com.agentframework.data.entity.User;
import com.agentframework.data.repository.UserRepository;
import com.agentframework.dto.ErrorResponse;
import com.agentframework.dto.auth.AuthResponse;
import com.agentframework.dto.auth.LoginRequest;
import com.agentframework.dto.auth.RegisterRequest;
import com.agentframework.security.JwtService;
import com.agentframework.security.RolePermissions;
import com.agentframework.security.UserDetailsImpl;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Authentication controller for login and registration.
 */
@Slf4j
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Tag(name = "Authentication", description = "User authentication endpoints - register, login, and token refresh")
public class AuthController {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuthenticationManager authenticationManager;

    private static final long TOKEN_EXPIRATION_SECONDS = 86400;  // 24 hours

    /**
     * Register a new user.
     */
    @Operation(summary = "Register a new user", description = "Creates a new user account and returns JWT tokens")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "User registered successfully",
                    content = @Content(schema = @Schema(implementation = AuthResponse.class))),
            @ApiResponse(responseCode = "400", description = "Invalid request data"),
            @ApiResponse(responseCode = "409", description = "Email already registered")
    })
    @PostMapping("/register")
    public ResponseEntity<?> register(@Valid @RequestBody RegisterRequest request) {
        log.info("Registration request for email: {}", request.getEmail());

        // Check if email already exists
        if (userRepository.existsByEmail(request.getEmail())) {
            log.warn("Registration failed: email already exists: {}", request.getEmail());
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(new ErrorResponse("Registration failed", "Email already registered"));
        }

        // Create new user
        User user = User.builder()
                .email(request.getEmail())
                .password(passwordEncoder.encode(request.getPassword()))
                .fullName(request.getFullName())
                .tenantId(request.getTenantId())
                .role(User.Role.USER)
                .enabled(true)
                .build();

        user = userRepository.save(user);
        log.info("User registered successfully: {} ({})", user.getEmail(), user.getId());

        // Generate tokens
        UserDetailsImpl userDetails = new UserDetailsImpl(user);
        String accessToken = jwtService.generateToken(userDetails);
        String refreshToken = jwtService.generateRefreshToken(userDetails);

        // Get effective scopes
        Set<String> scopes = getEffectiveScopes(user);

        return ResponseEntity.status(HttpStatus.CREATED).body(
                AuthResponse.builder()
                        .accessToken(accessToken)
                        .refreshToken(refreshToken)
                        .expiresIn(TOKEN_EXPIRATION_SECONDS)
                        .userId(user.getId().toString())
                        .email(user.getEmail())
                        .fullName(user.getFullName())
                        .tenantId(user.getTenantId())
                        .role(user.getRole().name())
                        .scopes(scopes)
                        .build()
        );
    }

    /**
     * Login with email and password.
     */
    @Operation(summary = "Login", description = "Authenticate with email and password to get JWT tokens")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Login successful",
                    content = @Content(schema = @Schema(implementation = AuthResponse.class))),
            @ApiResponse(responseCode = "401", description = "Invalid credentials")
    })
    @PostMapping("/login")
    public ResponseEntity<?> login(@Valid @RequestBody LoginRequest request) {
        log.info("Login request for email: {}", request.getEmail());

        try {
            // Authenticate
            Authentication authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(
                            request.getEmail(),
                            request.getPassword()
                    )
            );

            // Get user details
            UserDetailsImpl userDetails = (UserDetailsImpl) authentication.getPrincipal();

            // Generate tokens
            String accessToken = jwtService.generateToken(userDetails);
            String refreshToken = jwtService.generateRefreshToken(userDetails);

            log.info("User logged in successfully: {}", request.getEmail());

            // Get effective scopes from custom scopes or role defaults
            Set<String> scopes = getEffectiveScopesFromUserDetails(userDetails);

            return ResponseEntity.ok(
                    AuthResponse.builder()
                            .accessToken(accessToken)
                            .refreshToken(refreshToken)
                            .expiresIn(TOKEN_EXPIRATION_SECONDS)
                            .userId(userDetails.getId().toString())
                            .email(userDetails.getEmail())
                            .fullName(userDetails.getFullName())
                            .tenantId(userDetails.getTenantId())
                            .role(userDetails.getRole())
                            .scopes(scopes)
                            .build()
            );

        } catch (BadCredentialsException e) {
            log.warn("Login failed for email: {} - Invalid credentials", request.getEmail());
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new ErrorResponse("Login failed", "Invalid email or password"));
        }
    }

    /**
     * Refresh access token using refresh token.
     */
    @Operation(summary = "Refresh token", description = "Get a new access token using a valid refresh token")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Token refreshed successfully",
                    content = @Content(schema = @Schema(implementation = AuthResponse.class))),
            @ApiResponse(responseCode = "401", description = "Invalid or expired refresh token")
    })
    @PostMapping("/refresh")
    public ResponseEntity<?> refresh(@RequestBody RefreshRequest request) {
        try {
            String refreshToken = request.getRefreshToken();
            String userEmail = jwtService.extractUsername(refreshToken);

            User user = userRepository.findByEmail(userEmail)
                    .orElse(null);

            if (user == null) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(new ErrorResponse("Refresh failed", "Invalid refresh token"));
            }

            UserDetailsImpl userDetails = new UserDetailsImpl(user);

            if (!jwtService.isTokenValid(refreshToken, userDetails)) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(new ErrorResponse("Refresh failed", "Refresh token expired"));
            }

            String newAccessToken = jwtService.generateToken(userDetails);

            // Get effective scopes
            Set<String> scopes = getEffectiveScopes(user);

            return ResponseEntity.ok(
                    AuthResponse.builder()
                            .accessToken(newAccessToken)
                            .refreshToken(refreshToken)  // Return same refresh token
                            .expiresIn(TOKEN_EXPIRATION_SECONDS)
                            .userId(user.getId().toString())
                            .email(user.getEmail())
                            .fullName(user.getFullName())
                            .tenantId(user.getTenantId())
                            .role(user.getRole().name())
                            .scopes(scopes)
                            .build()
            );

        } catch (Exception e) {
            log.warn("Token refresh failed: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new ErrorResponse("Refresh failed", "Invalid refresh token"));
        }
    }

    /**
     * Get effective scopes from User entity.
     */
    private Set<String> getEffectiveScopes(User user) {
        // Check if user has custom scopes
        if (user.getCustomScopes() != null && !user.getCustomScopes().isEmpty()) {
            return Arrays.stream(user.getCustomScopes().split(","))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .collect(Collectors.toSet());
        }
        // Fall back to role-based permissions
        return RolePermissions.getScopes(user.getRole());
    }

    /**
     * Get effective scopes from UserDetailsImpl.
     */
    private Set<String> getEffectiveScopesFromUserDetails(UserDetailsImpl userDetails) {
        // Check if user has custom scopes
        if (userDetails.getCustomScopes() != null && !userDetails.getCustomScopes().isEmpty()) {
            return Arrays.stream(userDetails.getCustomScopes().split(","))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .collect(Collectors.toSet());
        }
        // Fall back to role-based permissions
        try {
            User.Role role = User.Role.valueOf(userDetails.getRole());
            return RolePermissions.getScopes(role);
        } catch (IllegalArgumentException e) {
            return RolePermissions.getScopes(User.Role.USER);
        }
    }

    /**
     * Simple DTO for refresh token request.
     */
    public record RefreshRequest(String refreshToken) {
        public String getRefreshToken() {
            return refreshToken;
        }
    }
}
