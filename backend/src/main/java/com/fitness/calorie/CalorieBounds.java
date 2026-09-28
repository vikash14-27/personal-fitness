package com.fitness.calorie;

public final class CalorieBounds {

  public static final int MIN_AGE = 16;
  public static final int MAX_AGE = 100;
  public static final String MIN_HEIGHT_CM = "120.0";
  public static final String MAX_HEIGHT_CM = "250.0";
  public static final String MIN_WEIGHT_KG = "30.0";
  public static final String MAX_WEIGHT_KG = "300.0";
  public static final int MIN_TIMELINE_WEEKS = 1;
  public static final int MAX_TIMELINE_WEEKS = 104;
  public static final int MALE_FLOOR_KCAL = 1500;
  public static final int FEMALE_FLOOR_KCAL = 1200;
  public static final int GOAL_CEILING_KCAL = 5000;
  public static final int OVERRIDE_MIN_KCAL = 1000;
  public static final int OVERRIDE_MAX_KCAL = 5000;
  public static final int KCAL_PER_KG = 7700;

  private CalorieBounds() {}
}
