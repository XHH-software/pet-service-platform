package com.pet.platform.security;

import com.pet.platform.common.UserContext;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;

/**
 * JWT 只携带 userId 与端标识（admin/user），管理端与小程序端各自使用独立请求头。
 */
@Component
public class JwtUtil {

    private final SecretKey key;
    private final Duration expire;

    public JwtUtil(JwtProperties properties) {
        this.key = Keys.hmacShaKeyFor(properties.getSecret().getBytes(StandardCharsets.UTF_8));
        this.expire = Duration.ofDays(properties.getExpireDays());
    }

    public String createToken(Long userId, String type) {
        Instant now = Instant.now();
        return Jwts.builder()
                .setSubject(String.valueOf(userId))
                .claim("type", type)
                .setIssuedAt(Date.from(now))
                .setExpiration(Date.from(now.plus(expire)))
                .signWith(key, SignatureAlgorithm.HS256)
                .compact();
    }

    public UserContext.LoginUser parse(String token) {
        Claims claims = Jwts.parserBuilder()
                .setSigningKey(key)
                .build()
                .parseClaimsJws(token)
                .getBody();
        return new UserContext.LoginUser(Long.valueOf(claims.getSubject()), claims.get("type", String.class));
    }

    public long remainingSeconds(String token) {
        try {
            Date expiration = Jwts.parserBuilder()
                    .setSigningKey(key).build()
                    .parseClaimsJws(token).getBody().getExpiration();
            long seconds = (expiration.getTime() - System.currentTimeMillis()) / 1000;
            return Math.max(seconds, 0);
        } catch (Exception e) {
            return 0;
        }
    }
}
