package com.fitness.auth;

import static org.mockito.ArgumentMatchers.any;
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
