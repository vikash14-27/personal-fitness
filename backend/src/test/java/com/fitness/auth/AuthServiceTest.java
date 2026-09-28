package com.fitness.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fitness.calorie.CalorieCalculator;
import com.fitness.common.ApiException;
import com.fitness.profile.CalorieGoalSource;
import com.fitness.profile.ProfileService;
import com.fitness.user.ActivityLevel;
import com.fitness.user.Sex;
import com.fitness.user.User;
import com.fitness.user.UserRepository;
import java.math.BigDecimal;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

  @Mock
  private UserRepository users;

  private PasswordEncoder passwordEncoder;
  private JwtService jwtService;
  private AuthService authService;

  @BeforeEach
  void setUp() {
    passwordEncoder = new BCryptPasswordEncoder(4);
    jwtService = new JwtService(new com.fitness.config.AppProperties(
        new com.fitness.config.AppProperties.Jwt("dev-only-jwt-secret-change-me-use-32b-min!!", 3_600_000),
        java.util.List.of("http://localhost:5173")));
    ProfileService profiles = new ProfileService(users, new CalorieCalculator());
    authService = new AuthService(users, passwordEncoder, jwtService, profiles);
  }

  @Test
  void signupStoresTheComputedGoalAndReturnsAToken() {
    when(users.existsByEmail("ada@example.com")).thenReturn(false);
    when(users.save(any(User.class))).thenAnswer(invocation -> {
      User saved = invocation.getArgument(0);
      saved.setId("user-1");
      return saved;
    });

    AuthResponse response = authService.signup(readmeSignup("Ada@Example.com"));

    assertThat(response.token()).isNotBlank();
    assertThat(jwtService.parseUserId(response.token())).isEqualTo("user-1");
    assertThat(response.profile().email()).isEqualTo("ada@example.com");
    assertThat(response.profile().computedCalorieGoal()).isEqualTo(2364);
    assertThat(response.profile().activeCalorieGoal()).isEqualTo(2364);
    assertThat(response.profile().calorieGoalSource()).isEqualTo(CalorieGoalSource.COMPUTED);
    assertThat(response.profile().calorieGoalOverride()).isNull();
    assertThat(passwordEncoder.matches("password123", capturedPasswordHash())).isTrue();
  }

  @Test
  void signupRejectsADuplicateEmail() {
    when(users.existsByEmail("ada@example.com")).thenReturn(true);

    assertThatThrownBy(() -> authService.signup(readmeSignup("ada@example.com")))
        .isInstanceOf(ApiException.class)
        .hasFieldOrPropertyWithValue("error", "email_taken");
    verify(users, never()).save(any());
  }

  @Test
  void signupTranslatesAUniqueIndexRace() {
    when(users.existsByEmail("ada@example.com")).thenReturn(false);
    when(users.save(any(User.class))).thenThrow(new DuplicateKeyException("E11000"));

    assertThatThrownBy(() -> authService.signup(readmeSignup("ada@example.com")))
        .isInstanceOf(ApiException.class)
        .hasFieldOrPropertyWithValue("error", "email_taken");
  }

  @Test
  void loginAcceptsTheRightPassword() {
    User user = storedUser();
    when(users.findByEmail("ada@example.com")).thenReturn(Optional.of(user));

    AuthResponse response = authService.login(new LoginRequest("Ada@Example.com", "password123"));

    assertThat(jwtService.parseUserId(response.token())).isEqualTo("user-1");
    assertThat(response.profile().activeCalorieGoal()).isEqualTo(2364);
  }

  @Test
  void loginRejectsAnUnknownEmailAndAWrongPasswordWithTheSameError() {
    when(users.findByEmail("missing@example.com")).thenReturn(Optional.empty());
    when(users.findByEmail("ada@example.com")).thenReturn(Optional.of(storedUser()));

    assertThatThrownBy(() -> authService.login(new LoginRequest("missing@example.com", "password123")))
        .isInstanceOf(ApiException.class)
        .hasFieldOrPropertyWithValue("error", "invalid_credentials")
        .hasMessage("Email or password is incorrect.");
    assertThatThrownBy(() -> authService.login(new LoginRequest("ada@example.com", "wrong-password")))
        .isInstanceOf(ApiException.class)
        .hasFieldOrPropertyWithValue("error", "invalid_credentials")
        .hasMessage("Email or password is incorrect.");
  }

  private String capturedPasswordHash() {
    org.mockito.ArgumentCaptor<User> captor = org.mockito.ArgumentCaptor.forClass(User.class);
    verify(users).save(captor.capture());
    return captor.getValue().getPasswordHash();
  }

  private User storedUser() {
    User user = new User();
    user.setId("user-1");
    user.setEmail("ada@example.com");
    user.setPasswordHash(passwordEncoder.encode("password123"));
    new ProfileService(users, new CalorieCalculator()).applyBio(
        user, 30, Sex.MALE, new BigDecimal("180"), new BigDecimal("90"), new BigDecimal("80"),
        ActivityLevel.MODERATELY_ACTIVE, 20, null);
    return user;
  }

  private static SignupRequest readmeSignup(String email) {
    return new SignupRequest(
        email,
        "password123",
        30,
        Sex.MALE,
        new BigDecimal("180"),
        new BigDecimal("90"),
        new BigDecimal("80"),
        ActivityLevel.MODERATELY_ACTIVE,
        20);
  }
}
