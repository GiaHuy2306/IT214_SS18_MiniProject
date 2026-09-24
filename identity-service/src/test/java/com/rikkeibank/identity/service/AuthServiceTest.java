package com.rikkeibank.identity.service;

import com.rikkeibank.common.dto.AuthRequest;
import com.rikkeibank.common.dto.AuthResponse;
import com.rikkeibank.common.dto.RegisterRequest;
import com.rikkeibank.common.enums.Role;
import com.rikkeibank.identity.entity.RefreshToken;
import com.rikkeibank.identity.entity.User;
import com.rikkeibank.identity.repository.RefreshTokenRepository;
import com.rikkeibank.identity.repository.UserRepository;
import com.rikkeibank.identity.security.JwtUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtUtils jwtUtils;

    @Mock
    private TokenBlacklistService blacklistService;

    @InjectMocks
    private AuthService authService;

    private User sampleUser;

    @BeforeEach
    void setUp() {
        sampleUser = User.builder()
                .id(1L)
                .username("customer")
                .password("hashed_pwd")
                .email("customer@rikkeibank.com")
                .role(Role.ROLE_CUSTOMER)
                .customerId(1L)
                .active(true)
                .build();
    }

    @Test
    @DisplayName("Should successfully authenticate and return access + refresh tokens")
    void testLoginSuccess() {
        AuthRequest req = AuthRequest.builder()
                .username("customer")
                .password("customer123")
                .build();

        when(userRepository.findByUsername("customer")).thenReturn(Optional.of(sampleUser));
        when(passwordEncoder.matches("customer123", "hashed_pwd")).thenReturn(true);
        when(jwtUtils.generateToken(sampleUser)).thenReturn("sample_jwt_token");
        when(jwtUtils.getExpirationMs()).thenReturn(3600000L);
        when(refreshTokenRepository.save(any(RefreshToken.class))).thenAnswer(i -> i.getArgument(0));

        AuthResponse resp = authService.login(req);

        assertNotNull(resp);
        assertEquals("sample_jwt_token", resp.getAccessToken());
        assertNotNull(resp.getRefreshToken());
        assertEquals("customer", resp.getUsername());
        assertEquals("ROLE_CUSTOMER", resp.getRole());
    }

    @Test
    @DisplayName("Should throw exception when password does not match")
    void testLoginInvalidPassword() {
        AuthRequest req = AuthRequest.builder()
                .username("customer")
                .password("wrong_password")
                .build();

        when(userRepository.findByUsername("customer")).thenReturn(Optional.of(sampleUser));
        when(passwordEncoder.matches("wrong_password", "hashed_pwd")).thenReturn(false);

        assertThrows(IllegalArgumentException.class, () -> authService.login(req));
    }

    @Test
    @DisplayName("Should register new user successfully")
    void testRegisterSuccess() {
        RegisterRequest req = RegisterRequest.builder()
                .username("newuser")
                .password("password123")
                .email("new@rikkeibank.com")
                .role(Role.ROLE_CUSTOMER)
                .build();

        when(userRepository.existsByUsername("newuser")).thenReturn(false);
        when(userRepository.existsByEmail("new@rikkeibank.com")).thenReturn(false);
        when(passwordEncoder.encode("password123")).thenReturn("encoded_pass");
        when(userRepository.save(any(User.class))).thenAnswer(i -> i.getArgument(0));

        User registered = authService.register(req);

        assertNotNull(registered);
        assertEquals("newuser", registered.getUsername());
        assertEquals("encoded_pass", registered.getPassword());
    }
}
