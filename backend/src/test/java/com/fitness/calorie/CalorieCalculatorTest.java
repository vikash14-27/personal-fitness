package com.fitness.calorie;

import static org.assertj.core.api.Assertions.assertThat;

import com.fitness.user.ActivityLevel;
import com.fitness.user.Sex;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class CalorieCalculatorTest {

  private final CalorieCalculator calculator = new CalorieCalculator();

  @Test
  void readmeWorkedExample_maleModerateLoss() {
    CalorieGoal goal = calculator.calculate(
        Sex.MALE, 30, bd("180"), bd("90"), bd("80"), ActivityLevel.MODERATELY_ACTIVE, 20);

    assertThat(goal.bmr()).isEqualTo(1880);
    assertThat(goal.tdee()).isEqualTo(2914);
    assertThat(goal.activityMultiplier()).isEqualByComparingTo("1.55");
    assertThat(goal.dailyAdjustment()).isEqualTo(550);
    assertThat(goal.computedGoal()).isEqualTo(2364);
    assertThat(goal.clamp()).isEqualTo(ClampReason.NONE);
  }

  @Test
  void maintenanceMatchesTdee() {
    CalorieGoal goal = calculator.calculate(
        Sex.MALE, 30, bd("180"), bd("90"), bd("90"), ActivityLevel.SEDENTARY, 12);

    assertThat(goal.bmr()).isEqualTo(1880);
    assertThat(goal.tdee()).isEqualTo(2256);
    assertThat(goal.dailyAdjustment()).isZero();
    assertThat(goal.computedGoal()).isEqualTo(2256);
    assertThat(goal.clamp()).isEqualTo(ClampReason.NONE);
  }

  @Test
  void weightGainAddsASurplus() {
    CalorieGoal goal = calculator.calculate(
        Sex.MALE, 25, bd("175"), bd("70"), bd("75"), ActivityLevel.LIGHTLY_ACTIVE, 10);

    assertThat(goal.bmr()).isEqualTo(1674);
    assertThat(goal.tdee()).isEqualTo(2301);
    assertThat(goal.dailyAdjustment()).isEqualTo(-550);
    assertThat(goal.computedGoal()).isEqualTo(2851);
    assertThat(goal.clamp()).isEqualTo(ClampReason.NONE);
  }

  @Test
  void aggressiveLossIsHeldAtTheFemaleFloor() {
    CalorieGoal goal = calculator.calculate(
        Sex.FEMALE, 40, bd("160"), bd("100"), bd("70"), ActivityLevel.SEDENTARY, 4);

    assertThat(goal.computedGoal()).isEqualTo(CalorieBounds.FEMALE_FLOOR_KCAL);
    assertThat(goal.clamp()).isEqualTo(ClampReason.FLOOR);
  }

  @Test
  void aggressiveGainIsHeldAtTheCeiling() {
    CalorieGoal goal = calculator.calculate(
        Sex.MALE, 20, bd("190"), bd("60"), bd("90"), ActivityLevel.EXTRA_ACTIVE, 4);

    assertThat(goal.computedGoal()).isEqualTo(CalorieBounds.GOAL_CEILING_KCAL);
    assertThat(goal.clamp()).isEqualTo(ClampReason.CEILING);
  }

  private static BigDecimal bd(String value) {
    return new BigDecimal(value);
  }
}
