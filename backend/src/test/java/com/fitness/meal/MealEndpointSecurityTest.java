package com.fitness.meal;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fitness.auth.JwtAuthFilter;
import com.fitness.auth.JwtService;
import com.fitness.common.GlobalExceptionHandler;
import com.fitness.config.AppProperties;
import com.fitness.config.RestAuthEntryPoint;
import com.fitness.config.SecurityConfig;
import com.fitness.profile.CalorieGoalSource;
import com.fitness.user.User;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = {MealController.class, DayController.class})
@AutoConfigureMockMvc
@Import({
  SecurityConfig.class,
  JwtAuthFilter.class,
  JwtService.class,
  RestAuthEntryPoint.class,
  GlobalExceptionHandler.class
})
@EnableConfigurationProperties(AppProperties.class)
@TestPropertySource(properties = {
  "app.jwt.secret=dev-only-jwt-secret-change-me-use-32b-min!!",
  "app.jwt.expiration-ms=3600000",
  "app.cors-origins=http://localhost:5173"
})
class MealEndpointSecurityTest {

  @Autowired
  private MockMvc mockMvc;

  @Autowired
  private JwtService jwt;

  @MockitoBean
  private MealEstimateService estimates;

  @MockitoBean
  private MealService meals;

  @Test
  void mealRoutesRequireABearerTokenBeforeTheControllerRuns() throws Exception {
    mockMvc.perform(post("/api/meals/estimate")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"text\":\"   \"}"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.error").value("unauthorized"));

    mockMvc.perform(get("/api/days/2026-09-28"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.error").value("unauthorized"));

    mockMvc.perform(post("/api/meals/estimate")
            .header(HttpHeaders.AUTHORIZATION, "Bearer not-a-jwt")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"text\":\"oatmeal\"}"))
        .andExpect(status().isUnauthorized());

    verify(estimates, never()).estimate(any(), any());
    verify(meals, never()).day(any(), any());
    verify(meals, never()).create(any(), any());
  }

  @Test
  void aValidTokenReachesEstimateSaveAndDayViewAsThatUser() throws Exception {
    when(estimates.estimate(eq("user-1"), eq("oatmeal")))
        .thenReturn(new MealEstimateResponse("Oatmeal", 150, 5, 27, 3));
    when(meals.create(eq("user-1"), any())).thenReturn(
        new MealResponse("meal-1", LocalDate.of(2026, 9, 28), "Oatmeal", 150, 5, 27, 3));
    when(meals.day(eq("user-1"), eq(LocalDate.of(2026, 9, 28)))).thenReturn(new DayResponse(
        LocalDate.of(2026, 9, 28),
        2000,
        CalorieGoalSource.COMPUTED,
        List.of(),
        new MacroTotals(0, 0, 0, 0),
        2000));

    String token = tokenFor("user-1");

    mockMvc.perform(post("/api/meals/estimate")
            .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"text\":\"oatmeal\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.calories").value(150));

    mockMvc.perform(post("/api/meals")
            .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {
                  "day": "2026-09-28",
                  "description": "Oatmeal",
                  "calories": 150,
                  "proteinG": 5,
                  "carbsG": 27,
                  "fatG": 3
                }
                """))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.id").value("meal-1"));

    mockMvc.perform(get("/api/days/2026-09-28")
            .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.calorieGoal").value(2000))
        .andExpect(jsonPath("$.remainingCalories").value(2000));

    verify(estimates).estimate("user-1", "oatmeal");
    verify(meals).create(eq("user-1"), any());
    verify(meals).day("user-1", LocalDate.of(2026, 9, 28));
  }

  private String tokenFor(String userId) {
    User user = new User();
    user.setId(userId);
    user.setEmail(userId + "@example.com");
    return jwt.generate(user);
  }
}
