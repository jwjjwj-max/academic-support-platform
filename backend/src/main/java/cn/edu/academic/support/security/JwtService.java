package cn.edu.academic.support.security;

import cn.edu.academic.support.user.model.UserAccount;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;

@Service
public class JwtService {
    private final SecretKey key;
    private final Duration accessTokenDuration;

    public JwtService(@Value("${app.security.jwt-secret}") String secret,
                      @Value("${app.security.access-token-minutes}") long accessTokenMinutes) {
        if (secret.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalArgumentException("JWT_SECRET 至少需要 32 字节");
        }
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.accessTokenDuration = Duration.ofMinutes(accessTokenMinutes);
    }

    public String issue(UserAccount user) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(String.valueOf(user.getId()))
                .claim("login", user.getLoginName())
                .claim("name", user.getDisplayName())
                .claim("type", user.getAccountType())
                .claim("tv", user.getTokenVersion())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(accessTokenDuration)))
                .signWith(key)
                .compact();
    }

    public Claims parse(String token) {
        return Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
    }

    public long expiresInSeconds() {
        return accessTokenDuration.toSeconds();
    }
}

