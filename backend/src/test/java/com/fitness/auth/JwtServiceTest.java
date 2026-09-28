package com.fitness.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fitness.config.AppProperties;
import com.fitness.user.User;
import io.jsonwebtoken.JwtException;
import java.util.List;
import org.junit.jupiter.api.Test;

class JwtServiceTest {

  @Test
  void roundTripReturnsTheUserId() {
    JwtService jwt = jwtService();
    User user = new User();
    user.setId("user-1");
    user.setEmail("ada@example.com");

    String token = jwt.generate(user);

    assertThat(jwt.parseUserId(token)).isEqualTo("user-1");
  }

  @Test
  void rejectsATamperedToken() {
    JwtService jwt = jwtService();
    User user = new User();
    user.setId("user-1");
    user.setEmail("ada@example.com");
    String token = jwt.generate(user);

    assertThatThrownBy(() -> jwt.parseUserId(token + "x")).isInstanceOf(JwtException.class);
  }

  @Test
  void rejectsAShortSecret() {
    AppProperties properties = new AppProperties(new AppProperties.Jwt("too-short", 60_000), List.of());
    assertThatThrownBy(() -> new JwtService(properties))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("32 bytes");
  }

  @Test
  void rejectsAnExpiredToken() {
    JwtService jwt = new JwtService(new AppProperties(
        new AppProperties.Jwt("dev-only-jwt-secret-change-me-use-32b-min!!", -60_000),
        List.of()));
    User user = new User();
    user.setId("user-1");
    user.setEmail("ada@example.com");

    String token = jwt.generate(user);

    assertThatThrownBy(() -> jwt.parseUserId(token)).isInstanceOf(JwtException.class);
  }

  @Test
  void rejectsATokenSignedWithADifferentSecret() {
    User user = new User();
    user.setId("user-1");
    user.setEmail("ada@example.com");
    String token = jwtService().generate(user);
    JwtService other = new JwtService(new AppProperties(
        new AppProperties.Jwt("another-dev-only-jwt-secret-32bytes!!", 60_000),
        List.of()));

    assertThatThrownBy(() -> other.parseUserId(token)).isInstanceOf(JwtException.class);
  }

  @Test
  void refusesToIssueATokenBeforeTheUserHasAnId() {
    User user = new User();
    user.setEmail("ada@example.com");

    assertThatThrownBy(() -> jwtService().generate(user))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("id");
  }

  private static JwtService jwtService() {
    return new JwtService(new AppProperties(
        new AppProperties.Jwt("dev-only-jwt-secret-change-me-use-32b-min!!", 3_600_000),
        List.of("http://localhost:5173")));
  }
}
