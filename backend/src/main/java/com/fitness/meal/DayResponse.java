package com.fitness.meal;

import com.fitness.profile.CalorieGoalSource;
import java.time.LocalDate;
import java.util.List;

public record DayResponse(
    LocalDate day,
    int calorieGoal,
    CalorieGoalSource calorieGoalSource,
    List<MealResponse> meals,
    MacroTotals totals,
    int remainingCalories) {}
