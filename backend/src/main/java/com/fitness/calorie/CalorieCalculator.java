package com.fitness.calorie;

import com.fitness.user.ActivityLevel;
import com.fitness.user.Sex;
import java.math.BigDecimal;
import java.math.RoundingMode;
import org.springframework.stereotype.Component;

/**
 * Mifflin–St Jeor BMR, an activity multiplier, then a daily deficit or surplus
 * from the gap between current and target weight over the chosen timeline.
 * See the README for the worked example and rounding rules.
 */
@Component
public class CalorieCalculator {

  public CalorieGoal calculate(
      Sex sex,
      int age,
      BigDecimal heightCm,
      BigDecimal currentWeightKg,
      BigDecimal targetWeightKg,
      ActivityLevel activityLevel,
      int timelineWeeks) {
    if (timelineWeeks < 1) {
      throw new IllegalArgumentException("timelineWeeks must be at least 1");
    }

    BigDecimal weight = currentWeightKg;
    BigDecimal height = heightCm;
    BigDecimal sexConstant = sex == Sex.MALE ? BigDecimal.valueOf(5) : BigDecimal.valueOf(-161);

    BigDecimal bmr = BigDecimal.TEN.multiply(weight)
        .add(new BigDecimal("6.25").multiply(height))
        .subtract(BigDecimal.valueOf(5L * age))
        .add(sexConstant);

    BigDecimal tdee = bmr.multiply(activityLevel.multiplier());
    BigDecimal days = BigDecimal.valueOf(timelineWeeks).multiply(BigDecimal.valueOf(7));
    BigDecimal adjustment = weight.subtract(targetWeightKg)
        .multiply(BigDecimal.valueOf(CalorieBounds.KCAL_PER_KG))
        .divide(days, 8, RoundingMode.HALF_UP);
    BigDecimal raw = tdee.subtract(adjustment);

    int roundedGoal = raw.setScale(0, RoundingMode.HALF_UP).intValueExact();
    int floor = sex == Sex.MALE ? CalorieBounds.MALE_FLOOR_KCAL : CalorieBounds.FEMALE_FLOOR_KCAL;
    int goal = roundedGoal;
    ClampReason clamp = ClampReason.NONE;
    if (goal < floor) {
      goal = floor;
      clamp = ClampReason.FLOOR;
    } else if (goal > CalorieBounds.GOAL_CEILING_KCAL) {
      goal = CalorieBounds.GOAL_CEILING_KCAL;
      clamp = ClampReason.CEILING;
    }

    return new CalorieGoal(
        bmr.setScale(0, RoundingMode.HALF_UP).intValueExact(),
        tdee.setScale(0, RoundingMode.HALF_UP).intValueExact(),
        activityLevel.multiplier(),
        adjustment.setScale(0, RoundingMode.HALF_UP).intValueExact(),
        goal,
        clamp);
  }
}
