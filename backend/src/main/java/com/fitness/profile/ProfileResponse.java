package com.fitness.profile;

import com.fitness.calorie.ClampReason;
import com.fitness.user.ActivityLevel;
import com.fitness.user.Sex;
import java.math.BigDecimal;

public record ProfileResponse(
    String id,
    String email,
    int age,
    Sex sex,
    double heightCm,
    double currentWeightKg,
    double targetWeightKg,
    ActivityLevel activityLevel,
    int timelineWeeks,
    int bmr,
    int tdee,
    BigDecimal activityMultiplier,
    int dailyAdjustment,
    int computedCalorieGoal,
    Integer calorieGoalOverride,
    int activeCalorieGoal,
    CalorieGoalSource calorieGoalSource,
    ClampReason calorieGoalClamp) {}
