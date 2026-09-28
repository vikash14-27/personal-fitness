package com.fitness.calorie;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fitness.user.ActivityLevel;
import com.fitness.user.Sex;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

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

  @Test
  void femaleMifflinStJeorUsesTheMinus161Constant() {
    CalorieGoal goal = calculator.calculate(
        Sex.FEMALE, 30, bd("165"), bd("60"), bd("60"), ActivityLevel.SEDENTARY, 12);

    assertThat(goal.bmr()).isEqualTo(1320);
    assertThat(goal.tdee()).isEqualTo(1584);
    assertThat(goal.dailyAdjustment()).isZero();
    assertThat(goal.computedGoal()).isEqualTo(1584);
    assertThat(goal.clamp()).isEqualTo(ClampReason.NONE);
  }

  @ParameterizedTest
  @CsvSource({
      "SEDENTARY,1.2,2256",
      "LIGHTLY_ACTIVE,1.375,2585",
      "MODERATELY_ACTIVE,1.55,2914",
      "VERY_ACTIVE,1.725,3243",
      "EXTRA_ACTIVE,1.9,3572"
  })
  void maintenanceScalesByEveryActivityMultiplier(ActivityLevel activity, String multiplier, int expected) {
    CalorieGoal goal = calculator.calculate(
        Sex.MALE, 30, bd("180"), bd("90"), bd("90"), activity, 20);

    assertThat(goal.bmr()).isEqualTo(1880);
    assertThat(goal.activityMultiplier()).isEqualByComparingTo(multiplier);
    assertThat(goal.tdee()).isEqualTo(expected);
    assertThat(goal.dailyAdjustment()).isZero();
    assertThat(goal.computedGoal()).isEqualTo(expected);
    assertThat(goal.clamp()).isEqualTo(ClampReason.NONE);
  }

  @Test
  void halfUpRoundsAnExactHalfKilocalorieAwayFromZero() {
    // 975 × 1.9 = 1852.5, which half-up rounds to 1853. Maintenance, so the goal matches TDEE.
    CalorieGoal goal = calculator.calculate(
        Sex.MALE, 16, bd("120"), bd("30"), bd("30"), ActivityLevel.EXTRA_ACTIVE, 20);

    assertThat(goal.bmr()).isEqualTo(975);
    assertThat(goal.tdee()).isEqualTo(1853);
    assertThat(goal.dailyAdjustment()).isZero();
    assertThat(goal.computedGoal()).isEqualTo(1853);
    assertThat(goal.clamp()).isEqualTo(ClampReason.NONE);
  }

  @Test
  void storedGoalRoundsOnceSoDisplayedPartsCanDisagreeByOneKilocalorie() {
    CalorieGoal goal = calculator.calculate(
        Sex.MALE, 16, bd("120"), bd("30"), bd("80"), ActivityLevel.LIGHTLY_ACTIVE, 52);

    assertThat(goal.tdee()).isEqualTo(1341);
    assertThat(goal.dailyAdjustment()).isEqualTo(-1058);
    assertThat(goal.tdee() - goal.dailyAdjustment()).isEqualTo(2399);
    assertThat(goal.computedGoal()).isEqualTo(2398);
    assertThat(goal.clamp()).isEqualTo(ClampReason.NONE);
  }

  @Test
  void oneDecimalMeasurementsAreIncludedBeforeRounding() {
    CalorieGoal goal = calculator.calculate(
        Sex.MALE, 30, bd("180.5"), bd("90.5"), bd("80.5"), ActivityLevel.MODERATELY_ACTIVE, 20);

    assertThat(goal.bmr()).isEqualTo(1888);
    assertThat(goal.tdee()).isEqualTo(2927);
    assertThat(goal.dailyAdjustment()).isEqualTo(550);
    assertThat(goal.computedGoal()).isEqualTo(2377);
    assertThat(goal.clamp()).isEqualTo(ClampReason.NONE);
  }

  @Test
  void maleGoalExactlyAtTheFloorStaysUnclamped() {
    CalorieGoal goal = calculator.calculate(
        Sex.MALE, 16, bd("120"), bd("57.5"), bd("57.5"), ActivityLevel.SEDENTARY, 12);

    assertThat(goal.tdee()).isEqualTo(CalorieBounds.MALE_FLOOR_KCAL);
    assertThat(goal.computedGoal()).isEqualTo(CalorieBounds.MALE_FLOOR_KCAL);
    assertThat(goal.clamp()).isEqualTo(ClampReason.NONE);
  }

  @Test
  void maleGoalOneKilocalorieUnderTheFloorIsRaised() {
    CalorieGoal goal = calculator.calculate(
        Sex.MALE, 16, bd("120"), bd("41.5"), bd("41.5"), ActivityLevel.LIGHTLY_ACTIVE, 12);

    assertThat(goal.tdee()).isEqualTo(1499);
    assertThat(goal.computedGoal()).isEqualTo(CalorieBounds.MALE_FLOOR_KCAL);
    assertThat(goal.clamp()).isEqualTo(ClampReason.FLOOR);
  }

  @Test
  void aggressiveLossIsHeldAtTheMaleFloor() {
    CalorieGoal goal = calculator.calculate(
        Sex.MALE, 80, bd("160"), bd("120"), bd("70"), ActivityLevel.SEDENTARY, 8);

    assertThat(goal.computedGoal()).isEqualTo(CalorieBounds.MALE_FLOOR_KCAL);
    assertThat(goal.clamp()).isEqualTo(ClampReason.FLOOR);
  }

  @Test
  void femaleGoalExactlyAtTheFloorStaysUnclamped() {
    CalorieGoal goal = calculator.calculate(
        Sex.FEMALE, 30, bd("160"), bd("31.1"), bd("31.1"), ActivityLevel.SEDENTARY, 12);

    assertThat(goal.bmr()).isEqualTo(1000);
    assertThat(goal.tdee()).isEqualTo(CalorieBounds.FEMALE_FLOOR_KCAL);
    assertThat(goal.computedGoal()).isEqualTo(CalorieBounds.FEMALE_FLOOR_KCAL);
    assertThat(goal.clamp()).isEqualTo(ClampReason.NONE);
  }

  @Test
  void goalExactlyAtTheCeilingStaysUnclamped() {
    CalorieGoal goal = calculator.calculate(
        Sex.MALE, 16, bd("165"), bd("268"), bd("268"), ActivityLevel.LIGHTLY_ACTIVE, 12);

    assertThat(goal.tdee()).isEqualTo(CalorieBounds.GOAL_CEILING_KCAL);
    assertThat(goal.computedGoal()).isEqualTo(CalorieBounds.GOAL_CEILING_KCAL);
    assertThat(goal.clamp()).isEqualTo(ClampReason.NONE);
  }

  @Test
  void goalOneKilocalorieOverTheCeilingIsLowered() {
    CalorieGoal goal = calculator.calculate(
        Sex.MALE, 16, bd("250"), bd("268"), bd("268"), ActivityLevel.SEDENTARY, 12);

    assertThat(goal.tdee()).isEqualTo(5001);
    assertThat(goal.computedGoal()).isEqualTo(CalorieBounds.GOAL_CEILING_KCAL);
    assertThat(goal.clamp()).isEqualTo(ClampReason.CEILING);
  }

  @Test
  void timelineShorterThanOneWeekIsRejected() {
    assertThatThrownBy(() -> calculator.calculate(
            Sex.MALE, 30, bd("180"), bd("90"), bd("80"), ActivityLevel.MODERATELY_ACTIVE, 0))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("timelineWeeks");
  }

  private static BigDecimal bd(String value) {
    return new BigDecimal(value);
  }
}
