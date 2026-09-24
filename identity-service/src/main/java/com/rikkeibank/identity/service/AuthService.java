package com.rikkeibank.identity.service;

import com.rikkeibank.common.dto.AuthRequest;
import com.rikkeibank.common.dto.AuthResponse;
import com.rikkeibank.common.dto.RefreshTokenRequest;
import com.rikkeibank.common.dto.RegisterRequest;
import com.rikkeibank.common.dto.RevokeTokenRequest;
import com.rikkeibank.common.enums.Role;
import com.rikkeibank.identity.entity.RefreshToken;
import com.rikkeibank.identity.entity.User;
import com.rikkeibank.identity.repository.RefreshTokenRepository;
import com.rikkeibank.identity.repository.UserRepository;
import com.rikkeibank.identity.security.JwtUtils;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtils jwtUtils;
    private final TokenBlacklistService blacklistService;

    @Value("${app.jwt.refresh-expiration-ms:604800000}")
    private long refreshExpirationMs;

    @PostConstruct
    public void initDefaultUsers() {
        if (userRepository.count() == 0) {
            log.info("Initializing default users: admin, teller, customer");
            userRepository.save(User.builder()
                    .username("admin")
                    .password(passwordEncoder.encode("admin123"))
                    .email("admin@rikkeibank.com")
                    .role(Role.ROLE_ADMIN)
                    .build());

            userRepository.save(User.builder()
                    .username("teller")
                    .password(passwordEncoder.encode("teller123"))
                    .email("teller@rikkeibank.com")
                    .role(Role.ROLE_TELLER)
                    .build());

            userRepository.save(User.builder()
                    .username("customer")
                    .password(passwordEncoder.encode("customer123"))
                    .email("customer@rikkeibank.com")
                    .role(Role.ROLE_CUSTOMER)
                    .customerId(1L)
                    .build());

            userRepository.save(User.builder()
                    .username("customer2")
                    .password(passwordEncoder.encode("customer123"))
                    .email("customer2@rikkeibank.com")
                    .role(Role.ROLE_CUSTOMER)
                    .customerId(2L)
                    .build());
        }
    }

    @Transactional
    public User register(RegisterRequest request) {
        if (userRepository.existsByUsername(request.getUsername())) {
            throw new IllegalArgumentException("Username already exists: " + request.getUsername());
        }
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new IllegalArgumentException("Email already exists: " + request.getEmail());
        }

        User user = User.builder()
                .username(request.getUsername())
                .password(passwordEncoder.encode(request.getPassword()))
                .email(request.getEmail())
                .role(request.getRole() != null ? request.getRole() : Role.ROLE_CUSTOMER)
                .customerId(request.getCustomerId())
                .active(true)
                .build();

        return userRepository.save(user);
    }

    @Transactional
    public AuthResponse login(AuthRequest request) {
        User user = userRepository.findByUsername(request.getUsername())
                .orElseThrow(() -> new IllegalArgumentException("Invalid username or password"));

        if (!user.isActive()) {
            throw new IllegalStateException("Account is inactive or locked");
        }

        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new IllegalArgumentException("Invalid username or password");
        }

        String accessToken = jwtUtils.generateToken(user);
        RefreshToken refreshToken = createRefreshToken(user.getUsername());

        return AuthResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken.getToken())
                .expiresIn(jwtUtils.getExpirationMs() / 1000)
                .username(user.getUsername())
                .role(user.getRole().name())
                .customerId(user.getCustomerId())
                .build();
    }

    @Transactional
    public AuthResponse refreshToken(RefreshTokenRequest request) {
        RefreshToken token = refreshTokenRepository.findByToken(request.getRefreshToken())
                .orElseThrow(() -> new IllegalArgumentException("Invalid refresh token"));

        if (token.isRevoked() || token.getExpiryDate().isBefore(Instant.now())) {
            refreshTokenRepository.delete(token);
            throw new IllegalStateException("Refresh token has expired or was revoked. Please log in again.");
        }

        User user = userRepository.findByUsername(token.getUsername())
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + token.getUsername()));

        String newAccessToken = jwtUtils.generateToken(user);

        return AuthResponse.builder()
                .accessToken(newAccessToken)
                .refreshToken(token.getToken())
                .expiresIn(jwtUtils.getExpirationMs() / 1000)
                .username(user.getUsername())
                .role(user.getRole().name())
                .customerId(user.getCustomerId())
                .build();
    }

    @Transactional
    public void revokeToken(RevokeTokenRequest request) {
        if (request.getToken() != null && !request.getToken().isBlank()) {
            long remainingMs = jwtUtils.getRemainingExpirationMs(request.getToken());
            blacklistService.blacklistToken(request.getToken(), remainingMs);
            log.info("Revoked access token with remaining TTL {} ms", remainingMs);
        }

        if (request.getUsername() != null && !request.getUsername().isBlank()) {
            refreshTokenRepository.deleteByUsername(request.getUsername());
            log.info("Revoked all refresh tokens for user: {}", request.getUsername());
        }
    }

    public User getUserByUsername(String username) {
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + username));
    }

    public List<User> getAllUsers() {
        return userRepository.findAll();
    }

    private RefreshToken createRefreshToken(String username) {
        refreshTokenRepository.deleteByUsername(username);

        RefreshToken refreshToken = RefreshToken.builder()
                .username(username)
                .token(UUID.randomUUID().toString().replace("-", ""))
                .expiryDate(Instant.now().plusMillis(refreshExpirationMs))
                .revoked(false)
                .build();

        return refreshTokenRepository.save(refreshToken);
    }
}
