package com.fitness.meal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fitness.common.ApiException;
import com.fitness.common.GlobalExceptionHandler;
import com.fitness.profile.CalorieGoalSource;
import java.time.LocalDate;
import java.util.List;
import org.mockito.ArgumentCaptor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

@ExtendWith(MockitoExtension.class)
class MealControllerValidationTest {

  @Mock
  private MealEstimateService estimates;

  @Mock
  private MealService meals;

  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
    validator.afterPropertiesSet();
    ObjectMapper mapper = new ObjectMapper()
        .registerModule(new JavaTimeModule())
        .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    mockMvc = MockMvcBuilders.standaloneSetup(new MealController(estimates, meals), new DayController(meals))
        .setControllerAdvice(new GlobalExceptionHandler())
        .setValidator(validator)
        .setMessageConverters(new MappingJackson2HttpMessageConverter(mapper))
        .build();
    SecurityContextHolder.getContext().setAuthentication(
        new UsernamePasswordAuthenticationToken("user-1", null, List.of()));
  }

  @AfterEach
  void clearAuth() {
    SecurityContextHolder.clearContext();
  }

  @Test
  void blankEstimateTextIsRejected() throws Exception {
    mockMvc.perform(post("/api/meals/estimate")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"text\":\"   \"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error").value("validation_failed"))
        .andExpect(jsonPath("$.fields.text").value("Describe the meal"));

    verify(estimates, never()).estimate(any(), any());
  }

  @Test
  void estimateFailureIsReturnedWithoutSaving() throws Exception {
    when(estimates.estimate(eq("user-1"), eq("two eggs")))
        .thenThrow(new ApiException(
            HttpStatus.SERVICE_UNAVAILABLE,
            "estimate_unavailable",
            "Meal estimates need an NVIDIA API key."));

    mockMvc.perform(post("/api/meals/estimate")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"text\":\"two eggs\"}"))
        .andExpect(status().isServiceUnavailable())
        .andExpect(jsonPath("$.error").value("estimate_unavailable"));

    verify(meals, never()).create(any(), any());
  }

  @Test
  void fractionalCaloriesAndMacrosAreRejected() throws Exception {
    mockMvc.perform(post("/api/meals")
            .contentType(MediaType.APPLICATION_JSON)
            .content(body("380.5", "18")))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error").value("invalid_body"));

    mockMvc.perform(post("/api/meals")
            .contentType(MediaType.APPLICATION_JSON)
            .content(body("380", "18.25")))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error").value("validation_failed"))
        .andExpect(jsonPath("$.fields.proteinG").exists());

    verify(meals, never()).create(any(), any());
  }

  @Test
  void createUpdateDeleteAndDayViewUseTheSignedInUser() throws Exception {
    when(meals.create(eq("user-1"), any())).thenReturn(sampleMeal());
    when(meals.update(eq("user-1"), eq("meal-1"), any())).thenReturn(sampleMeal());
    when(meals.day(eq("user-1"), eq(LocalDate.of(2026, 9, 28)))).thenReturn(new DayResponse(
        LocalDate.of(2026, 9, 28),
        2364,
        CalorieGoalSource.COMPUTED,
        List.of(sampleMeal()),
        new MacroTotals(380, 18, 22, 16.5),
        1984));

    mockMvc.perform(post("/api/meals")
            .contentType(MediaType.APPLICATION_JSON)
            .content(body("380", "18")))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.id").value("meal-1"));

    mockMvc.perform(put("/api/meals/meal-1")
            .contentType(MediaType.APPLICATION_JSON)
            .content(body("380", "18")))
        .andExpect(status().isOk());

    mockMvc.perform(delete("/api/meals/meal-1"))
        .andExpect(status().isNoContent());

    mockMvc.perform(get("/api/days/2026-09-28"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.calorieGoal").value(2364))
        .andExpect(jsonPath("$.totals.calories").value(380))
        .andExpect(jsonPath("$.remainingCalories").value(1984))
        .andExpect(jsonPath("$.meals[0].description").value("Two eggs"));

    verify(meals).delete("user-1", "meal-1");
  }

  @Test
  void estimateTextLongerThan500CharactersIsRejected() throws Exception {
    mockMvc.perform(post("/api/meals/estimate")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"text\":\"" + "a".repeat(501) + "\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error").value("validation_failed"))
        .andExpect(jsonPath("$.fields.text").value("Meal description must be 500 characters or fewer"));

    verify(estimates, never()).estimate(any(), any());
  }

  @Test
  void anEstimateOf500CharactersIsReturnedAndNotSaved() throws Exception {
    String text = "a".repeat(500);
    when(estimates.estimate(eq("user-1"), eq(text)))
        .thenReturn(new MealEstimateResponse("Oats", 150, 5.0, 27.0, 3.0));

    mockMvc.perform(post("/api/meals/estimate")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"text\":\"" + text + "\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.description").value("Oats"))
        .andExpect(jsonPath("$.calories").value(150))
        .andExpect(jsonPath("$.id").doesNotExist());

    verify(meals, never()).create(any(), any());
  }

  @Test
  void missingEstimateTextIsRejected() throws Exception {
    mockMvc.perform(post("/api/meals/estimate")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error").value("validation_failed"))
        .andExpect(jsonPath("$.fields.text").value("Describe the meal"));

    verify(estimates, never()).estimate(any(), any());
  }

  @Test
  void aFailedEstimateDoesNotSave() throws Exception {
    when(estimates.estimate(eq("user-1"), eq("soup")))
        .thenThrow(new ApiException(
            HttpStatus.BAD_GATEWAY,
            "estimate_failed",
            "Couldn't estimate that meal. The estimate service returned HTTP 500."));

    mockMvc.perform(post("/api/meals/estimate")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"text\":\"soup\"}"))
        .andExpect(status().isBadGateway())
        .andExpect(jsonPath("$.error").value("estimate_failed"));

    verify(meals, never()).create(any(), any());
  }

  @Test
  void saveRejectsNameCalorieAndMacroBounds() throws Exception {
    mockMvc.perform(post("/api/meals")
            .contentType(MediaType.APPLICATION_JSON)
            .content(mealJson("a".repeat(301), "100", "1", "1", "1")))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error").value("validation_failed"))
        .andExpect(jsonPath("$.fields.description").value("Use 300 characters or fewer"));

    mockMvc.perform(post("/api/meals")
            .contentType(MediaType.APPLICATION_JSON)
            .content(mealJson("   ", "100", "1", "1", "1")))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.fields.description").value("Name the meal"));

    mockMvc.perform(post("/api/meals")
            .contentType(MediaType.APPLICATION_JSON)
            .content(mealJson("Eggs", "10001", "1", "1", "1")))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.fields.calories").value("Calories must be between 0 and 10000"));

    mockMvc.perform(post("/api/meals")
            .contentType(MediaType.APPLICATION_JSON)
            .content(mealJson("Eggs", "-1", "1", "1", "1")))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.fields.calories").value("Calories must be between 0 and 10000"));

    mockMvc.perform(post("/api/meals")
            .contentType(MediaType.APPLICATION_JSON)
            .content(mealJson("Eggs", "0.5", "1", "1", "1")))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error").value("invalid_body"));

    mockMvc.perform(post("/api/meals")
            .contentType(MediaType.APPLICATION_JSON)
            .content(mealJson("Eggs", "10000.5", "1", "1", "1")))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error").value("invalid_body"));

    mockMvc.perform(post("/api/meals")
            .contentType(MediaType.APPLICATION_JSON)
            .content(mealJson("Eggs", "100", "1000.1", "1", "1")))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.fields.proteinG").value("Protein must be between 0 and 1000 g"));

    mockMvc.perform(post("/api/meals")
            .contentType(MediaType.APPLICATION_JSON)
            .content(mealJson("Eggs", "100", "1", "-0.1", "1")))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.fields.carbsG").exists());

    mockMvc.perform(post("/api/meals")
            .contentType(MediaType.APPLICATION_JSON)
            .content(mealJson("Eggs", "100", "1", "1", "1.25")))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.fields.fatG").value("Fat can have at most one decimal place"));

    mockMvc.perform(post("/api/meals")
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {"description":"Eggs","calories":100,"proteinG":1,"carbsG":1,"fatG":1}
                """))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.fields.day").value("Day is required"));

    mockMvc.perform(post("/api/meals")
            .contentType(MediaType.APPLICATION_JSON)
            .content(mealJson("Eggs", "100", "1", "1", "1").replace("2026-09-28", "09/28/2026")))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error").value("invalid_body"));

    verify(meals, never()).create(any(), any());
  }

  @Test
  void saveAcceptsBoundaryValuesIncludingATrailingZeroCalorie() throws Exception {
    when(meals.create(eq("user-1"), any())).thenReturn(sampleMeal());

    mockMvc.perform(post("/api/meals")
            .contentType(MediaType.APPLICATION_JSON)
            .content(mealJson("a".repeat(300), "0", "0", "0.0", "0")))
        .andExpect(status().isCreated());

    mockMvc.perform(post("/api/meals")
            .contentType(MediaType.APPLICATION_JSON)
            .content(mealJson("Feast", "10000", "1000", "1000.0", "0.5")))
        .andExpect(status().isCreated());

    mockMvc.perform(post("/api/meals")
            .contentType(MediaType.APPLICATION_JSON)
            .content(mealJson("Eggs", "380.0", "18.0", "22", "16.5")))
        .andExpect(status().isCreated());

    ArgumentCaptor<SaveMealRequest> captor = ArgumentCaptor.forClass(SaveMealRequest.class);
    verify(meals, times(3)).create(eq("user-1"), captor.capture());
    assertThatCalories(captor.getAllValues().get(0), 0, 300);
    assertThat(captor.getAllValues().get(1).calories()).isEqualTo(10000);
    assertThat(captor.getAllValues().get(1).proteinG()).isEqualByComparingTo("1000");
    assertThat(captor.getAllValues().get(2).calories()).isEqualTo(380);
  }

  @Test
  void fractionalCaloriesOnUpdateAreRejected() throws Exception {
    mockMvc.perform(put("/api/meals/meal-1")
            .contentType(MediaType.APPLICATION_JSON)
            .content(body("380.5", "18")))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error").value("invalid_body"));

    verify(meals, never()).update(any(), any(), any());
  }

  @Test
  void anotherUsersMealIsNotFoundOnEditOrDelete() throws Exception {
    when(meals.update(eq("user-1"), eq("other-meal"), any()))
        .thenThrow(new ApiException(HttpStatus.NOT_FOUND, "meal_not_found", "Meal not found."));
    doThrow(new ApiException(HttpStatus.NOT_FOUND, "meal_not_found", "Meal not found."))
        .when(meals).delete("user-1", "other-meal");

    mockMvc.perform(put("/api/meals/other-meal")
            .contentType(MediaType.APPLICATION_JSON)
            .content(body("380", "18")))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.error").value("meal_not_found"));

    mockMvc.perform(delete("/api/meals/other-meal"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.error").value("meal_not_found"));
  }

  @Test
  void aBadDayPathIsRejected() throws Exception {
    mockMvc.perform(get("/api/days/not-a-date"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error").value("invalid_request"));

    verify(meals, never()).day(any(), any());
  }

  @Test
  void signedOutRequestsAreRejected() throws Exception {
    SecurityContextHolder.clearContext();

    mockMvc.perform(get("/api/days/2026-09-28"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.error").value("unauthorized"));

    mockMvc.perform(post("/api/meals/estimate")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"text\":\"two eggs\"}"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.error").value("unauthorized"));

    mockMvc.perform(post("/api/meals")
            .contentType(MediaType.APPLICATION_JSON)
            .content(body("380", "18")))
        .andExpect(status().isUnauthorized());

    mockMvc.perform(put("/api/meals/meal-1")
            .contentType(MediaType.APPLICATION_JSON)
            .content(body("380", "18")))
        .andExpect(status().isUnauthorized());

    mockMvc.perform(delete("/api/meals/meal-1"))
        .andExpect(status().isUnauthorized());

    verify(estimates, never()).estimate(any(), any());
    verify(meals, never()).create(any(), any());
    verify(meals, never()).update(any(), any(), any());
    verify(meals, never()).delete(any(), any());
    verify(meals, never()).day(any(), any());
  }

  private static void assertThatCalories(SaveMealRequest request, int calories, int descriptionLength) {
    assertThat(request.calories()).isEqualTo(calories);
    assertThat(request.description()).hasSize(descriptionLength);
  }

  private static String mealJson(
      String description, String calories, String protein, String carbs, String fat) {
    return """
        {
          "day": "2026-09-28",
          "description": "%s",
          "calories": %s,
          "proteinG": %s,
          "carbsG": %s,
          "fatG": %s
        }
        """.formatted(description, calories, protein, carbs, fat);
  }

  private static String body(String calories, String protein) {
    return """
        {
          "day": "2026-09-28",
          "description": "Two eggs",
          "calories": %s,
          "proteinG": %s,
          "carbsG": 22,
          "fatG": 16.5
        }
        """.formatted(calories, protein);
  }

  private static MealResponse sampleMeal() {
    return new MealResponse("meal-1", LocalDate.of(2026, 9, 28), "Two eggs", 380, 18, 22, 16.5);
  }
}
