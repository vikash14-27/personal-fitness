package com.fitness.calorie;

import java.math.BigDecimal;

public record CalorieGoal(
    int bmr,
    int tdee,
    BigDecimal activityMultiplier,
    int dailyAdjustment,
    int computedGoal,
    ClampReason clamp) {}
