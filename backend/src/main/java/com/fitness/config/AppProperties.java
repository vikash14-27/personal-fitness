package com.fitness.config;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app")
public record AppProperties(Jwt jwt, List<String> corsOrigins) {

  public record Jwt(String secret, long expirationMs) {}
}
