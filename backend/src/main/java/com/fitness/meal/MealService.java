package com.fitness.meal;

import com.fitness.common.ApiException;
import com.fitness.profile.ProfileResponse;
import com.fitness.profile.ProfileService;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class MealService {

  private final MealRepository meals;
  private final ProfileService profiles;

  public MealService(MealRepository meals, ProfileService profiles) {
    this.meals = meals;
    this.profiles = profiles;
  }

  public MealResponse create(String userId, SaveMealRequest request) {
    requireDay(request.day());
    Meal meal = new Meal();
    meal.setUserId(userId);
    apply(meal, request);
    Instant now = Instant.now();
    meal.setCreatedAt(now);
    meal.setUpdatedAt(now);
    return toResponse(meals.save(meal));
  }

  public MealResponse update(String userId, String mealId, SaveMealRequest request) {
    requireDay(request.day());
    Meal meal = requireOwned(userId, mealId);
    apply(meal, request);
    meal.setUpdatedAt(Instant.now());
    return toResponse(meals.save(meal));
  }

  public void delete(String userId, String mealId) {
    meals.delete(requireOwned(userId, mealId));
  }

  public DayResponse day(String userId, LocalDate day) {
    ProfileResponse profile = profiles.get(userId);
    List<MealResponse> mealsForDay = meals.findByUserIdAndDayOrderByCreatedAtAsc(userId, day).stream()
        .map(MealService::toResponse)
        .toList();
    MacroTotals totals = totals(mealsForDay);
    int remaining = Math.subtractExact(profile.activeCalorieGoal(), totals.calories());
    return new DayResponse(
        day,
        profile.activeCalorieGoal(),
        profile.calorieGoalSource(),
        mealsForDay,
        totals,
        remaining);
  }

  private Meal requireOwned(String userId, String mealId) {
    return meals.findByIdAndUserId(mealId, userId)
        .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "meal_not_found", "Meal not found."));
  }

  private static void requireDay(LocalDate day) {
    LocalDate latest = LocalDate.now().plusYears(2);
    if (day.isBefore(MealBounds.MIN_DAY) || day.isAfter(latest)) {
      throw new ApiException(
          HttpStatus.BAD_REQUEST,
          "validation_failed",
          "Day must be between 2000-01-01 and two years from today.",
          Map.of("day", "Day must be between 2000-01-01 and two years from today."));
    }
  }

  private static void apply(Meal meal, SaveMealRequest request) {
    meal.setDay(request.day());
    meal.setDescription(request.description().trim().replaceAll("\\s+", " "));
    meal.setCalories(request.calories());
    meal.setProteinG(oneDecimal(request.proteinG()));
    meal.setCarbsG(oneDecimal(request.carbsG()));
    meal.setFatG(oneDecimal(request.fatG()));
  }

  private static MacroTotals totals(List<MealResponse> meals) {
    int calories = 0;
    BigDecimal protein = BigDecimal.ZERO;
    BigDecimal carbs = BigDecimal.ZERO;
    BigDecimal fat = BigDecimal.ZERO;
    for (MealResponse meal : meals) {
      calories = Math.addExact(calories, meal.calories());
      protein = protein.add(BigDecimal.valueOf(meal.proteinG()));
      carbs = carbs.add(BigDecimal.valueOf(meal.carbsG()));
      fat = fat.add(BigDecimal.valueOf(meal.fatG()));
    }
    return new MacroTotals(calories, oneDecimal(protein), oneDecimal(carbs), oneDecimal(fat));
  }

  private static MealResponse toResponse(Meal meal) {
    return new MealResponse(
        meal.getId(),
        meal.getDay(),
        meal.getDescription(),
        meal.getCalories(),
        oneDecimal(BigDecimal.valueOf(meal.getProteinG())),
        oneDecimal(BigDecimal.valueOf(meal.getCarbsG())),
        oneDecimal(BigDecimal.valueOf(meal.getFatG())));
  }

  private static double oneDecimal(BigDecimal value) {
    return value.setScale(1, RoundingMode.HALF_UP).doubleValue();
  }
}
