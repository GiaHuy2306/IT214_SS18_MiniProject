package com.rikkeibank.gateway.filter;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rikkeibank.common.dto.ErrorResponse;
import com.rikkeibank.gateway.security.JwtUtils;
import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter implements GlobalFilter, Ordered {

    private final JwtUtils jwtUtils;
    private final ReactiveStringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private static final List<String> PUBLIC_ENDPOINTS = List.of(
            "/api/auth/login",
            "/api/auth/register",
            "/api/auth/refresh",
            "/api/auth/validate",
            "/actuator",
            "/eureka"
    );

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String path = request.getPath().value();

        // Check if endpoint is public
        if (isPublicEndpoint(path)) {
            return chain.filter(exchange);
        }

        // Verify Authorization header
        String authHeader = request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            log.warn("Missing or invalid Authorization header for URI: {}", path);
            return onError(exchange, HttpStatus.UNAUTHORIZED, "Missing or invalid Authorization header");
        }

        String token = authHeader.substring(7);

        // Validate token signature & expiry
        if (!jwtUtils.validateToken(token)) {
            log.warn("Invalid JWT token for URI: {}", path);
            return onError(exchange, HttpStatus.UNAUTHORIZED, "Invalid or expired JWT token");
        }

        // Check Redis blacklist for revoked tokens / force-logout
        String blacklistKey = "jwt:blacklist:" + token;
        return redisTemplate.hasKey(blacklistKey)
                .onErrorReturn(false) // Graceful fallback if Redis connection is unavailable
                .flatMap(isBlacklisted -> {
                    if (Boolean.TRUE.equals(isBlacklisted)) {
                        log.warn("Attempt to use revoked/blacklisted token for URI: {}", path);
                        return onError(exchange, HttpStatus.UNAUTHORIZED, "Token has been revoked. Please log in again.");
                    }

                    try {
                        Claims claims = jwtUtils.extractAllClaims(token);
                        String username = claims.getSubject();
                        String role = (String) claims.get("role");
                        Object userId = claims.get("userId");
                        Object customerId = claims.get("customerId");

                        ServerHttpRequest.Builder builder = request.mutate()
                                .header("X-User-Username", username != null ? username : "")
                                .header("X-User-Role", role != null ? role : "")
                                .header("X-User-Id", userId != null ? String.valueOf(userId) : "")
                                .header("X-User-Customer-Id", customerId != null ? String.valueOf(customerId) : "");

                        return chain.filter(exchange.mutate().request(builder.build()).build());
                    } catch (Exception e) {
                        log.error("Failed to extract claims from token: {}", e.getMessage());
                        return onError(exchange, HttpStatus.UNAUTHORIZED, "Failed to parse authentication claims");
                    }
                });
    }

    private boolean isPublicEndpoint(String path) {
        return PUBLIC_ENDPOINTS.stream().anyMatch(path::startsWith);
    }

    private Mono<Void> onError(ServerWebExchange exchange, HttpStatus status, String message) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(status);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);

        ErrorResponse errorBody = ErrorResponse.builder()
                .timestamp(LocalDateTime.now())
                .status(status.value())
                .error(status.getReasonPhrase())
                .message(message)
                .path(exchange.getRequest().getPath().value())
                .build();

        byte[] bytes;
        try {
            bytes = objectMapper.writeValueAsBytes(errorBody);
        } catch (JsonProcessingException e) {
            bytes = ("{\"error\":\"" + message + "\"}").getBytes(StandardCharsets.UTF_8);
        }

        DataBuffer buffer = response.bufferFactory().wrap(bytes);
        return response.writeWith(Mono.just(buffer));
    }

    @Override
    public int getOrder() {
        return -100; // Run early in the filter chain
    }
}
