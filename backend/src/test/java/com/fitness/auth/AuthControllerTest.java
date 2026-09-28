package com.fitness.auth;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fitness.calorie.ClampReason;
import com.fitness.common.ApiException;
import com.fitness.common.GlobalExceptionHandler;
import com.fitness.profile.CalorieGoalSource;
import com.fitness.profile.ProfileResponse;
import com.fitness.user.ActivityLevel;
import com.fitness.user.Sex;
import java.math.BigDecimal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

@ExtendWith(MockitoExtension.class)
class AuthControllerTest {

  @Mock
  private AuthService authService;

  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
    validator.afterPropertiesSet();
    mockMvc = MockMvcBuilders.standaloneSetup(new AuthController(authService))
        .setControllerAdvice(new GlobalExceptionHandler())
        .setValidator(validator)
        .build();
  }

  @Test
  void signupReturnsCreatedWithTheProfile() throws Exception {
    when(authService.signup(any())).thenReturn(new AuthResponse("token-1", sampleProfile()));

    mockMvc.perform(post("/api/auth/signup")
            .contentType(MediaType.APPLICATION_JSON)
            .content(validSignupBody()))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.token").value("token-1"))
        .andExpect(jsonPath("$.profile.activeCalorieGoal").value(2364))
        .andExpect(jsonPath("$.profile.calorieGoalSource").value("COMPUTED"));
    verify(authService).signup(any());
  }

  @Test
  void signupReportsFieldErrors() throws Exception {
    mockMvc.perform(post("/api/auth/signup")
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {
                  "email": "not-an-email",
                  "password": "short",
                  "age": 10,
                  "sex": "MALE",
                  "heightCm": 90,
                  "currentWeightKg": 10,
                  "targetWeightKg": 400,
                  "activityLevel": "SEDENTARY",
                  "timelineWeeks": 0
                }
                """))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error").value("validation_failed"))
        .andExpect(jsonPath("$.fields.email").value("Enter a valid email address"))
        .andExpect(jsonPath("$.fields.password").value("Password must be 8–72 characters"))
        .andExpect(jsonPath("$.fields.age").value("Age must be between 16 and 100"))
        .andExpect(jsonPath("$.fields.heightCm").value("Height must be between 120 and 250 cm"))
        .andExpect(jsonPath("$.fields.currentWeightKg").value("Current weight must be between 30 and 300 kg"))
        .andExpect(jsonPath("$.fields.targetWeightKg").value("Target weight must be between 30 and 300 kg"))
        .andExpect(jsonPath("$.fields.timelineWeeks").value("Timeline must be between 1 and 104 weeks"));
  }

  @Test
  void duplicateEmailIsAConflict() throws Exception {
    when(authService.signup(any())).thenThrow(
        new ApiException(HttpStatus.CONFLICT, "email_taken", "An account with this email already exists."));

    mockMvc.perform(post("/api/auth/signup")
            .contentType(MediaType.APPLICATION_JSON)
            .content(validSignupBody()))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.error").value("email_taken"))
        .andExpect(jsonPath("$.message").value("An account with this email already exists."));
  }

  @Test
  void signupAcceptsInclusiveBounds() throws Exception {
    when(authService.signup(any())).thenReturn(new AuthResponse("token-1", sampleProfile()));

    mockMvc.perform(post("/api/auth/signup")
            .contentType(MediaType.APPLICATION_JSON)
            .content(signupJson("ada@example.com", "p".repeat(8), 16, "FEMALE", "120.0", "30.0", "30.0", "SEDENTARY", 1)))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.token").value("token-1"));

    mockMvc.perform(post("/api/auth/signup")
            .contentType(MediaType.APPLICATION_JSON)
            .content(signupJson("max@example.com", "p".repeat(72), 100, "MALE", "250.0", "300.0", "300.0", "EXTRA_ACTIVE", 104)))
        .andExpect(status().isCreated());
  }

  @Test
  void signupRejectsValuesOutsideTheBounds() throws Exception {
    mockMvc.perform(post("/api/auth/signup")
            .contentType(MediaType.APPLICATION_JSON)
            .content(signupJson("ada@example.com", "short", 15, "MALE", "119.9", "29.9", "29.9", "SEDENTARY", 0)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error").value("validation_failed"))
        .andExpect(jsonPath("$.fields.password").value("Password must be 8–72 characters"))
        .andExpect(jsonPath("$.fields.age").value("Age must be between 16 and 100"))
        .andExpect(jsonPath("$.fields.heightCm").value("Height must be between 120 and 250 cm"))
        .andExpect(jsonPath("$.fields.currentWeightKg").value("Current weight must be between 30 and 300 kg"))
        .andExpect(jsonPath("$.fields.targetWeightKg").value("Target weight must be between 30 and 300 kg"))
        .andExpect(jsonPath("$.fields.timelineWeeks").value("Timeline must be between 1 and 104 weeks"));

    mockMvc.perform(post("/api/auth/signup")
            .contentType(MediaType.APPLICATION_JSON)
            .content(signupJson("ada@example.com", "p".repeat(73), 101, "MALE", "250.1", "300.1", "300.1", "SEDENTARY", 105)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error").value("validation_failed"))
        .andExpect(jsonPath("$.fields.password").value("Password must be 8–72 characters"))
        .andExpect(jsonPath("$.fields.age").value("Age must be between 16 and 100"))
        .andExpect(jsonPath("$.fields.heightCm").value("Height must be between 120 and 250 cm"))
        .andExpect(jsonPath("$.fields.currentWeightKg").value("Current weight must be between 30 and 300 kg"))
        .andExpect(jsonPath("$.fields.targetWeightKg").value("Target weight must be between 30 and 300 kg"))
        .andExpect(jsonPath("$.fields.timelineWeeks").value("Timeline must be between 1 and 104 weeks"));
    verify(authService, never()).signup(any());
  }

  @Test
  void signupRejectsMoreThanOneDecimalPlace() throws Exception {
    mockMvc.perform(post("/api/auth/signup")
            .contentType(MediaType.APPLICATION_JSON)
            .content(signupJson("ada@example.com", "password123", 30, "MALE", "180.12", "90.55", "80.01", "MODERATELY_ACTIVE", 20)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error").value("validation_failed"))
        .andExpect(jsonPath("$.fields.heightCm").value("Height can have at most one decimal place"))
        .andExpect(jsonPath("$.fields.currentWeightKg").value("Current weight can have at most one decimal place"))
        .andExpect(jsonPath("$.fields.targetWeightKg").value("Target weight can have at most one decimal place"));
  }

  @Test
  void signupRejectsMissingRequiredFields() throws Exception {
    mockMvc.perform(post("/api/auth/signup")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error").value("validation_failed"))
        .andExpect(jsonPath("$.fields.email").value("Email is required"))
        .andExpect(jsonPath("$.fields.password").value("Password is required"))
        .andExpect(jsonPath("$.fields.age").value("Age is required"))
        .andExpect(jsonPath("$.fields.sex").value("Sex is required"))
        .andExpect(jsonPath("$.fields.heightCm").value("Height is required"))
        .andExpect(jsonPath("$.fields.currentWeightKg").value("Current weight is required"))
        .andExpect(jsonPath("$.fields.targetWeightKg").value("Target weight is required"))
        .andExpect(jsonPath("$.fields.activityLevel").value("Activity level is required"))
        .andExpect(jsonPath("$.fields.timelineWeeks").value("Timeline is required"));
  }

  @Test
  void signupRejectsAnUnknownSexOrActivityLevel() throws Exception {
    mockMvc.perform(post("/api/auth/signup")
            .contentType(MediaType.APPLICATION_JSON)
            .content(signupJson("ada@example.com", "password123", 30, "OTHER", "180", "90", "80", "MODERATELY_ACTIVE", 20)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error").value("invalid_body"))
        .andExpect(jsonPath("$.message").value("The request body is invalid. Check sex, activity level, and numeric fields."));

    mockMvc.perform(post("/api/auth/signup")
            .contentType(MediaType.APPLICATION_JSON)
            .content(signupJson("ada@example.com", "password123", 30, "MALE", "180", "90", "80", "JOGGING", 20)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error").value("invalid_body"));
  }

  @Test
  void loginReturnsAToken() throws Exception {
    when(authService.login(any())).thenReturn(new AuthResponse("token-1", sampleProfile()));

    mockMvc.perform(post("/api/auth/login")
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {"email":"ada@example.com","password":"password123"}
                """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.token").value("token-1"))
        .andExpect(jsonPath("$.profile.activeCalorieGoal").value(2364))
        .andExpect(jsonPath("$.profile.calorieGoalSource").value("COMPUTED"));
    verify(authService).login(any());
  }

  @Test
  void loginRejectsInvalidCredentials() throws Exception {
    when(authService.login(any())).thenThrow(
        new ApiException(HttpStatus.UNAUTHORIZED, "invalid_credentials", "Email or password is incorrect."));

    mockMvc.perform(post("/api/auth/login")
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {"email":"ada@example.com","password":"wrong-password"}
                """))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.error").value("invalid_credentials"))
        .andExpect(jsonPath("$.message").value("Email or password is incorrect."));
  }

  @Test
  void loginReportsFieldErrors() throws Exception {
    mockMvc.perform(post("/api/auth/login")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error").value("validation_failed"))
        .andExpect(jsonPath("$.fields.email").value("Email is required"))
        .andExpect(jsonPath("$.fields.password").value("Password is required"));

    mockMvc.perform(post("/api/auth/login")
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {"email":"not-an-email","password":"%s"}
                """.formatted("p".repeat(73))))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.fields.email").value("Enter a valid email address"))
        .andExpect(jsonPath("$.fields.password").value("Password must be 8–72 characters"));
    verify(authService, never()).login(any());
  }

  @Test
  void logoutMapsToNoContent() throws Exception {
    mockMvc.perform(post("/api/auth/logout"))
        .andExpect(status().isNoContent());
  }

  private static String signupJson(
      String email,
      String password,
      int age,
      String sex,
      String height,
      String current,
      String target,
      String activity,
      int weeks) {
    return """
        {
          "email": "%s",
          "password": "%s",
          "age": %d,
          "sex": "%s",
          "heightCm": %s,
          "currentWeightKg": %s,
          "targetWeightKg": %s,
          "activityLevel": "%s",
          "timelineWeeks": %d
        }
        """.formatted(email, password, age, sex, height, current, target, activity, weeks);
  }

  private static String validSignupBody() {
    return """
        {
          "email": "ada@example.com",
          "password": "password123",
          "age": 30,
          "sex": "MALE",
          "heightCm": 180,
          "currentWeightKg": 90,
          "targetWeightKg": 80,
          "activityLevel": "MODERATELY_ACTIVE",
          "timelineWeeks": 20
        }
        """;
  }

  private static ProfileResponse sampleProfile() {
    return new ProfileResponse(
        "user-1",
        "ada@example.com",
        30,
        Sex.MALE,
        180,
        90,
        80,
        ActivityLevel.MODERATELY_ACTIVE,
        20,
        1880,
        2914,
        new BigDecimal("1.55"),
        550,
        2364,
        null,
        2364,
        CalorieGoalSource.COMPUTED,
        ClampReason.NONE);
  }
}
