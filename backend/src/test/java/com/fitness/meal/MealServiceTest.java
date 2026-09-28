package com.fitness.meal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fitness.calorie.ClampReason;
import com.fitness.common.ApiException;
import com.fitness.profile.CalorieGoalSource;
import com.fitness.profile.ProfileResponse;
import com.fitness.profile.ProfileService;
import com.fitness.user.ActivityLevel;
import com.fitness.user.Sex;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

@ExtendWith(MockitoExtension.class)
class MealServiceTest {

  @Mock
  private MealRepository meals;

  @Mock
  private ProfileService profiles;

  private MealService service;

  @BeforeEach
  void setUp() {
    service = new MealService(meals, profiles);
  }

  @Test
  void savesAMealForTheAuthenticatedUser() {
    when(meals.save(any(Meal.class))).thenAnswer(invocation -> {
      Meal meal = invocation.getArgument(0);
      meal.setId("meal-1");
      return meal;
    });

    MealResponse saved = service.create("user-1", request(LocalDate.of(2026, 9, 28), "380", "18.0"));

    ArgumentCaptor<Meal> captor = ArgumentCaptor.forClass(Meal.class);
    verify(meals).save(captor.capture());
    assertThat(captor.getValue().getUserId()).isEqualTo("user-1");
    assertThat(captor.getValue().getDescription()).isEqualTo("Two eggs");
    assertThat(saved.id()).isEqualTo("meal-1");
    assertThat(saved.calories()).isEqualTo(380);
    assertThat(saved.proteinG()).isEqualTo(18.0);
  }

  @Test
  void rejectsADayOutsideTheSupportedRange() {
    assertThatThrownBy(() -> service.create("user-1", request(LocalDate.of(1999, 12, 31), "100", "1")))
        .isInstanceOf(ApiException.class)
        .extracting("status", "error")
        .containsExactly(HttpStatus.BAD_REQUEST, "validation_failed");
    verify(meals, never()).save(any());
  }

  @Test
  void editAndDeleteStayOnTheOwningUser() {
    Meal owned = stored("meal-1", "user-1");
    when(meals.findByIdAndUserId("meal-1", "user-1")).thenReturn(Optional.of(owned));
    when(meals.save(any(Meal.class))).thenAnswer(invocation -> invocation.getArgument(0));
    when(meals.findByIdAndUserId("meal-1", "user-2")).thenReturn(Optional.empty());

    MealResponse updated = service.update("user-1", "meal-1", request(LocalDate.of(2026, 9, 29), "400", "20"));
    assertThat(updated.day()).isEqualTo(LocalDate.of(2026, 9, 29));
    assertThat(updated.calories()).isEqualTo(400);
    assertThat(owned.getUserId()).isEqualTo("user-1");

    assertThatThrownBy(() -> service.delete("user-2", "meal-1"))
        .isInstanceOf(ApiException.class)
        .extracting("status", "error")
        .containsExactly(HttpStatus.NOT_FOUND, "meal_not_found");
    verify(meals, never()).delete(any(Meal.class));

    service.delete("user-1", "meal-1");
    verify(meals).delete(owned);
  }

  @Test
  void dayViewTotalsMealsAgainstTheActiveGoal() {
    when(profiles.get("user-1")).thenReturn(profile(2200, CalorieGoalSource.OVERRIDE));
    when(meals.findByUserIdAndDayOrderByCreatedAtAsc("user-1", LocalDate.of(2026, 9, 28)))
        .thenReturn(List.of(
            stored("meal-1", "user-1", 400, 20.2, 30.0, 12.4),
            stored("meal-2", "user-1", 250, 10.1, 5.5, 8.0)));

    DayResponse day = service.day("user-1", LocalDate.of(2026, 9, 28));

    assertThat(day.calorieGoal()).isEqualTo(2200);
    assertThat(day.calorieGoalSource()).isEqualTo(CalorieGoalSource.OVERRIDE);
    assertThat(day.meals()).extracting(MealResponse::id).containsExactly("meal-1", "meal-2");
    assertThat(day.totals().calories()).isEqualTo(650);
    assertThat(day.totals().proteinG()).isEqualTo(30.3);
    assertThat(day.totals().carbsG()).isEqualTo(35.5);
    assertThat(day.totals().fatG()).isEqualTo(20.4);
    assertThat(day.remainingCalories()).isEqualTo(1550);
  }

  private static SaveMealRequest request(LocalDate day, String calories, String protein) {
    return new SaveMealRequest(
        day,
        "  Two   eggs ",
        new BigDecimal(calories).intValueExact(),
        new BigDecimal(protein),
        new BigDecimal("22"),
        new BigDecimal("16.5"));
  }

  private static Meal stored(String id, String userId) {
    return stored(id, userId, 380, 18, 22, 16.5);
  }

  private static Meal stored(
      String id, String userId, int calories, double protein, double carbs, double fat) {
    Meal meal = new Meal();
    meal.setId(id);
    meal.setUserId(userId);
    meal.setDay(LocalDate.of(2026, 9, 28));
    meal.setDescription("Two eggs");
    meal.setCalories(calories);
    meal.setProteinG(protein);
    meal.setCarbsG(carbs);
    meal.setFatG(fat);
    meal.setCreatedAt(Instant.parse("2026-09-28T12:00:00Z"));
    meal.setUpdatedAt(Instant.parse("2026-09-28T12:00:00Z"));
    return meal;
  }

  private static ProfileResponse profile(int activeGoal, CalorieGoalSource source) {
    boolean override = source == CalorieGoalSource.OVERRIDE;
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
        override ? activeGoal : null,
        activeGoal,
        source,
        ClampReason.NONE);
  }
}
