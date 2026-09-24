package com.rikkeibank.identity.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class TokenBlacklistService {

    private final StringRedisTemplate redisTemplate;
    private final Set<String> localBlacklistFallback = ConcurrentHashMap.newKeySet();

    private static final String BLACKLIST_PREFIX = "jwt:blacklist:";

    public void blacklistToken(String token, long expirationMs) {
        String key = BLACKLIST_PREFIX + token;
        try {
            redisTemplate.opsForValue().set(key, "revoked", expirationMs, TimeUnit.MILLISECONDS);
            log.info("Blacklisted token in Redis for {} ms", expirationMs);
        } catch (Exception ex) {
            log.warn("Redis error blacklisting token, falling back to local memory: {}", ex.getMessage());
            localBlacklistFallback.add(token);
        }
    }

    public boolean isBlacklisted(String token) {
        if (localBlacklistFallback.contains(token)) {
            return true;
        }
        try {
            String key = BLACKLIST_PREFIX + token;
            return Boolean.TRUE.equals(redisTemplate.hasKey(key));
        } catch (Exception ex) {
            log.warn("Redis error checking blacklist: {}", ex.getMessage());
            return localBlacklistFallback.contains(token);
        }
    }
}
