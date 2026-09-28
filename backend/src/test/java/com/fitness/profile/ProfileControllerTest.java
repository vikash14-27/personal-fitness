package com.fitness.profile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fitness.calorie.ClampReason;
import com.fitness.common.ApiException;
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
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

@ExtendWith(MockitoExtension.class)
class ProfileControllerTest {

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
    SecurityContextHolder.getContext().setAuthentication(
        new UsernamePasswordAuthenticationToken("user-1", null, List.of()));
  }

  @AfterEach
  void clearSecurity() {
    SecurityContextHolder.clearContext();
  }

  @Test
  void getReturnsTheCurrentProfile() throws Exception {
    when(profiles.get("user-1")).thenReturn(sample(null, 2364, CalorieGoalSource.COMPUTED));

    mockMvc.perform(get("/api/profile"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.email").value("ada@example.com"))
        .andExpect(jsonPath("$.computedCalorieGoal").value(2364))
        .andExpect(jsonPath("$.activeCalorieGoal").value(2364))
        .andExpect(jsonPath("$.calorieGoalSource").value("COMPUTED"));
    verify(profiles).get("user-1");
  }

  @Test
  void getWithoutAPrincipalIsUnauthorized() throws Exception {
    SecurityContextHolder.clearContext();

    mockMvc.perform(get("/api/profile"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.error").value("unauthorized"))
        .andExpect(jsonPath("$.message").value("Sign in required."));
    verify(profiles, never()).get(any());
  }

  @Test
  void putReplacesBioAndPassesANullOverrideThrough() throws Exception {
    when(profiles.update(eq("user-1"), any())).thenReturn(sample(null, 2914, CalorieGoalSource.COMPUTED));

    mockMvc.perform(put("/api/profile")
            .contentType(MediaType.APPLICATION_JSON)
            .content(profileJson(90, "null")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.activeCalorieGoal").value(2914))
        .andExpect(jsonPath("$.calorieGoalSource").value("COMPUTED"));

    ArgumentCaptor<UpdateProfileRequest> captor = ArgumentCaptor.forClass(UpdateProfileRequest.class);
    verify(profiles).update(eq("user-1"), captor.capture());
    assertThat(captor.getValue().calorieGoalOverride()).isNull();
    assertThat(captor.getValue().targetWeightKg()).isEqualByComparingTo("90");
  }

  @Test
  void putAcceptsOverrideBoundsAndKeepsComputedGoalInTheResponse() throws Exception {
    when(profiles.update(eq("user-1"), any())).thenReturn(sample(1000, 2364, CalorieGoalSource.OVERRIDE));

    mockMvc.perform(put("/api/profile")
            .contentType(MediaType.APPLICATION_JSON)
            .content(profileJson(80, "1000")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.calorieGoalOverride").value(1000))
        .andExpect(jsonPath("$.computedCalorieGoal").value(2364))
        .andExpect(jsonPath("$.activeCalorieGoal").value(1000))
        .andExpect(jsonPath("$.calorieGoalSource").value("OVERRIDE"));

    when(profiles.update(eq("user-1"), any())).thenReturn(sample(5000, 2364, CalorieGoalSource.OVERRIDE));
    mockMvc.perform(put("/api/profile")
            .contentType(MediaType.APPLICATION_JSON)
            .content(profileJson(80, "5000")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.calorieGoalOverride").value(5000))
        .andExpect(jsonPath("$.computedCalorieGoal").value(2364))
        .andExpect(jsonPath("$.calorieGoalSource").value("OVERRIDE"));
  }

  @Test
  void putRejectsOverrideOutside1000To5000() throws Exception {
    mockMvc.perform(put("/api/profile")
            .contentType(MediaType.APPLICATION_JSON)
            .content(profileJson(80, "999")))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error").value("validation_failed"))
        .andExpect(jsonPath("$.fields.calorieGoalOverride").value("Manual calorie goal must be between 1000 and 5000 kcal"));

    mockMvc.perform(put("/api/profile")
            .contentType(MediaType.APPLICATION_JSON)
            .content(profileJson(80, "5001")))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.fields.calorieGoalOverride").value("Manual calorie goal must be between 1000 and 5000 kcal"));
    verify(profiles, never()).update(any(), any());
  }

  @Test
  void putRejectsANonNumericOverride() throws Exception {
    mockMvc.perform(put("/api/profile")
            .contentType(MediaType.APPLICATION_JSON)
            .content(profileJson(80, "\"lots\"")))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error").value("invalid_body"));
    verify(profiles, never()).update(any(), any());
  }

  @Test
  void putRejectsBioOutsideTheBounds() throws Exception {
    mockMvc.perform(put("/api/profile")
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {
                  "age": 15,
                  "sex": "MALE",
                  "heightCm": 119.9,
                  "currentWeightKg": 29.9,
                  "targetWeightKg": 300.1,
                  "activityLevel": "SEDENTARY",
                  "timelineWeeks": 105,
                  "calorieGoalOverride": null
                }
                """))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error").value("validation_failed"))
        .andExpect(jsonPath("$.fields.age").value("Age must be between 16 and 100"))
        .andExpect(jsonPath("$.fields.heightCm").value("Height must be between 120 and 250 cm"))
        .andExpect(jsonPath("$.fields.currentWeightKg").value("Current weight must be between 30 and 300 kg"))
        .andExpect(jsonPath("$.fields.targetWeightKg").value("Target weight must be between 30 and 300 kg"))
        .andExpect(jsonPath("$.fields.timelineWeeks").value("Timeline must be between 1 and 104 weeks"));
  }

  @Test
  void putRejectsMoreThanOneDecimalPlace() throws Exception {
    mockMvc.perform(put("/api/profile")
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {
                  "age": 30,
                  "sex": "FEMALE",
                  "heightCm": 180.12,
                  "currentWeightKg": 90.55,
                  "targetWeightKg": 80.01,
                  "activityLevel": "VERY_ACTIVE",
                  "timelineWeeks": 20
                }
                """))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error").value("validation_failed"))
        .andExpect(jsonPath("$.fields.heightCm").value("Height can have at most one decimal place"))
        .andExpect(jsonPath("$.fields.currentWeightKg").value("Current weight can have at most one decimal place"))
        .andExpect(jsonPath("$.fields.targetWeightKg").value("Target weight can have at most one decimal place"));
  }

  @Test
  void missingProfileIsUnauthorized() throws Exception {
    when(profiles.get("user-1")).thenThrow(
        new ApiException(HttpStatus.UNAUTHORIZED, "unauthorized", "Sign in required."));

    mockMvc.perform(get("/api/profile"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.error").value("unauthorized"));
  }

  private static String profileJson(int targetWeightKg, String overrideJson) {
    return """
        {
          "age": 30,
          "sex": "MALE",
          "heightCm": 180,
          "currentWeightKg": 90,
          "targetWeightKg": %d,
          "activityLevel": "MODERATELY_ACTIVE",
          "timelineWeeks": 20,
          "calorieGoalOverride": %s
        }
        """.formatted(targetWeightKg, overrideJson);
  }

  private static ProfileResponse sample(Integer override, int computed, CalorieGoalSource source) {
    int active = override == null ? computed : override;
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
        computed,
        override,
        active,
        source,
        ClampReason.NONE);
  }
}
