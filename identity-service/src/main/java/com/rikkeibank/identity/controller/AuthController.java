package com.rikkeibank.identity.controller;

import com.rikkeibank.common.dto.*;
import com.rikkeibank.identity.entity.User;
import com.rikkeibank.identity.security.JwtUtils;
import com.rikkeibank.identity.service.AuthService;
import com.rikkeibank.identity.service.TokenBlacklistService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final JwtUtils jwtUtils;
    private final TokenBlacklistService blacklistService;

    @PostMapping("/register")
    public ResponseEntity<ApiResponse<User>> register(@Valid @RequestBody RegisterRequest request) {
        User user = authService.register(request);
        user.setPassword("******"); // mask password
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("User registered successfully", user));
    }

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<AuthResponse>> login(@Valid @RequestBody AuthRequest request) {
        AuthResponse response = authService.login(request);
        return ResponseEntity.ok(ApiResponse.success("Authentication successful", response));
    }

    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse<AuthResponse>> refreshToken(@Valid @RequestBody RefreshTokenRequest request) {
        AuthResponse response = authService.refreshToken(request);
        return ResponseEntity.ok(ApiResponse.success("Token refreshed successfully", response));
    }

    @PostMapping("/revoke")
    public ResponseEntity<ApiResponse<String>> revokeToken(
            @RequestHeader(value = "X-User-Role", required = false) String roleHeader,
            @RequestBody RevokeTokenRequest request) {
        // Enforce ADMIN permission if role header is passed from Gateway
        if (roleHeader != null && !roleHeader.contains("ADMIN")) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(ApiResponse.error(HttpStatus.FORBIDDEN.value(), "Access denied: ADMIN role required to revoke tokens"));
        }

        authService.revokeToken(request);
        return ResponseEntity.ok(ApiResponse.success("Token/session successfully revoked. User forced logout.", "REVOKED"));
    }

    @GetMapping("/validate")
    public ResponseEntity<ApiResponse<Map<String, Object>>> validateToken(@RequestParam("token") String token) {
        boolean valid = jwtUtils.validateToken(token);
        boolean blacklisted = blacklistService.isBlacklisted(token);

        if (!valid || blacklisted) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.error(HttpStatus.UNAUTHORIZED.value(), "Token is invalid or blacklisted"));
        }

        var claims = jwtUtils.extractAllClaims(token);
        return ResponseEntity.ok(ApiResponse.success("Token is valid", Map.of(
                "valid", true,
                "username", claims.getSubject(),
                "role", claims.get("role"),
                "userId", claims.get("userId")
        )));
    }

    @GetMapping("/users")
    public ResponseEntity<ApiResponse<List<User>>> getAllUsers(
            @RequestHeader(value = "X-User-Role", required = false) String roleHeader) {
        if (roleHeader != null && !roleHeader.contains("ADMIN")) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(ApiResponse.error(HttpStatus.FORBIDDEN.value(), "Access denied: ADMIN role required"));
        }
        List<User> users = authService.getAllUsers();
        users.forEach(u -> u.setPassword("******"));
        return ResponseEntity.ok(ApiResponse.success(users));
    }
}
