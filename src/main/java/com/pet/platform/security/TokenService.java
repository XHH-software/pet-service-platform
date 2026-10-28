package com.pet.platform.security;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

/**
 * token 黑名单：退出登录后剩余有效期内禁止再次使用。
 */
@Service
@RequiredArgsConstructor
public class TokenService {

    private static final String BLACKLIST_PREFIX = "jwt:blacklist:";

    private final StringRedisTemplate redisTemplate;
    private final JwtUtil jwtUtil;

    public void blacklist(String token) {
        long seconds = jwtUtil.remainingSeconds(token);
        if (seconds <= 0) {
            return;
        }
        try {
            redisTemplate.opsForValue().set(BLACKLIST_PREFIX + token, "1", seconds, TimeUnit.SECONDS);
        } catch (Exception e) {
            // Redis 不可用时退化为“token 到期自动失效”，不影响退出登录本身
        }
    }

    public boolean isBlacklisted(String token) {
        try {
            return Boolean.TRUE.equals(redisTemplate.hasKey(BLACKLIST_PREFIX + token));
        } catch (Exception e) {
            return false;
        }
    }
}
