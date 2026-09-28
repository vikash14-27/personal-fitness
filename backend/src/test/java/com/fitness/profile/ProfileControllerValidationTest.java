package com.fitness.profile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fitness.calorie.ClampReason;
import com.fitness.common.GlobalExceptionHandler;
import com.fitness.user.ActivityLevel;
import com.fitness.user.Sex;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

@ExtendWith(MockitoExtension.class)
class ProfileControllerValidationTest {

  @Mock
  private ProfileService profiles;

  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
    validator.afterPropertiesSet();
    mockMvc = MockMvcBuilders.standaloneSetup(new ProfileController(profiles))
        .setControllerAdvice(new GlobalExceptionHandler())
        .setValidator(validator)
        .build();
  }

  @AfterEach
  void clearAuth() {
    SecurityContextHolder.clearContext();
  }

  @Test
  void fractionalManualCalorieGoalIsRejected() throws Exception {
    mockMvc.perform(put("/api/profile")
            .contentType(MediaType.APPLICATION_JSON)
            .content(body("2200.5")))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error").value("invalid_body"));

    verify(profiles, never()).update(any(), any());
  }

  @Test
  void integerOverrideIsStoredAndNullClearsIt() throws Exception {
    SecurityContextHolder.getContext().setAuthentication(
        new UsernamePasswordAuthenticationToken("user-1", null, List.of()));
    when(profiles.update(eq("user-1"), any())).thenReturn(sampleProfile(2200), sampleProfile(null));

    mockMvc.perform(put("/api/profile")
            .contentType(MediaType.APPLICATION_JSON)
            .content(body("2200")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.calorieGoalSource").value("OVERRIDE"))
        .andExpect(jsonPath("$.activeCalorieGoal").value(2200));

    mockMvc.perform(put("/api/profile")
            .contentType(MediaType.APPLICATION_JSON)
            .content(body("null")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.calorieGoalSource").value("COMPUTED"));

    ArgumentCaptor<UpdateProfileRequest> captor = ArgumentCaptor.forClass(UpdateProfileRequest.class);
    verify(profiles, times(2)).update(eq("user-1"), captor.capture());
    assertThat(captor.getAllValues().get(0).calorieGoalOverride()).isEqualTo(2200);
    assertThat(captor.getAllValues().get(1).calorieGoalOverride()).isNull();
  }

  private static String body(String overrideJson) {
    return """
        {
          "age": 30,
          "sex": "MALE",
          "heightCm": 180,
          "currentWeightKg": 90,
          "targetWeightKg": 80,
          "activityLevel": "MODERATELY_ACTIVE",
          "timelineWeeks": 20,
          "calorieGoalOverride": %s
        }
        """.formatted(overrideJson);
  }

  private static ProfileResponse sampleProfile(Integer override) {
    boolean overridden = override != null;
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
        override,
        overridden ? override : 2364,
        overridden ? CalorieGoalSource.OVERRIDE : CalorieGoalSource.COMPUTED,
        ClampReason.NONE);
  }
}
