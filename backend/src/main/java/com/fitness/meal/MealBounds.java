package com.fitness.meal;

import java.time.LocalDate;

public final class MealBounds {

  public static final int MAX_DESCRIPTION = 300;
  public static final int MAX_ESTIMATE_TEXT = 500;
  public static final int MIN_CALORIES = 0;
  public static final int MAX_CALORIES = 10000;
  public static final String MIN_MACRO = "0";
  public static final String MAX_MACRO = "1000";
  public static final LocalDate MIN_DAY = LocalDate.of(2000, 1, 1);

  private MealBounds() {}
}
