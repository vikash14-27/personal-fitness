package com.fitness.auth;

import com.fitness.config.AppProperties;
import com.fitness.user.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import javax.crypto.SecretKey;
import org.springframework.stereotype.Service;

@Service
public class JwtService {

  static final String ISSUER = "fitness-api";

  private final SecretKey key;
  private final long expirationMs;

  public JwtService(AppProperties properties) {
    byte[] secret = properties.jwt().secret().getBytes(StandardCharsets.UTF_8);
    if (secret.length < 32) {
      throw new IllegalStateException("JWT_SECRET must be at least 32 bytes");
    }
    this.key = Keys.hmacShaKeyFor(secret);
    this.expirationMs = properties.jwt().expirationMs();
  }

  public String generate(User user) {
    if (user.getId() == null || user.getId().isBlank()) {
      throw new IllegalStateException("Cannot issue a token before the user has an id");
    }
    Instant now = Instant.now();
    return Jwts.builder()
        .issuer(ISSUER)
        .subject(user.getId())
        .claim("email", user.getEmail())
        .issuedAt(Date.from(now))
        .expiration(Date.from(now.plusMillis(expirationMs)))
        .signWith(key)
        .compact();
  }

  public String parseUserId(String token) {
    Claims claims = Jwts.parser()
        .verifyWith(key)
        .requireIssuer(ISSUER)
        .build()
        .parseSignedClaims(token)
        .getPayload();
    return claims.getSubject();
  }
}
