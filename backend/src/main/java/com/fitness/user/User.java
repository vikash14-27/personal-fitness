package com.fitness.user;

import com.fitness.calorie.ClampReason;
import java.time.Instant;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "users")
public class User {

  @Id
  private String id;

  @Indexed(unique = true)
  private String email;

  private String passwordHash;
  private int age;
  private Sex sex;
  private double heightCm;
  private double currentWeightKg;
  private double targetWeightKg;
  private ActivityLevel activityLevel;
  private int timelineWeeks;
  private int computedCalorieGoal;
  private Integer calorieGoalOverride;
  private ClampReason calorieGoalClamp;
  private Instant createdAt;
  private Instant updatedAt;

  public String getId() {
    return id;
  }

  public void setId(String id) {
    this.id = id;
  }

  public String getEmail() {
    return email;
  }

  public void setEmail(String email) {
    this.email = email;
  }

  public String getPasswordHash() {
    return passwordHash;
  }

  public void setPasswordHash(String passwordHash) {
    this.passwordHash = passwordHash;
  }

  public int getAge() {
    return age;
  }

  public void setAge(int age) {
    this.age = age;
  }

  public Sex getSex() {
    return sex;
  }

  public void setSex(Sex sex) {
    this.sex = sex;
  }

  public double getHeightCm() {
    return heightCm;
  }

  public void setHeightCm(double heightCm) {
    this.heightCm = heightCm;
  }

  public double getCurrentWeightKg() {
    return currentWeightKg;
  }

  public void setCurrentWeightKg(double currentWeightKg) {
    this.currentWeightKg = currentWeightKg;
  }

  public double getTargetWeightKg() {
    return targetWeightKg;
  }

  public void setTargetWeightKg(double targetWeightKg) {
    this.targetWeightKg = targetWeightKg;
  }

  public ActivityLevel getActivityLevel() {
    return activityLevel;
  }

  public void setActivityLevel(ActivityLevel activityLevel) {
    this.activityLevel = activityLevel;
  }

  public int getTimelineWeeks() {
    return timelineWeeks;
  }

  public void setTimelineWeeks(int timelineWeeks) {
    this.timelineWeeks = timelineWeeks;
  }

  public int getComputedCalorieGoal() {
    return computedCalorieGoal;
  }

  public void setComputedCalorieGoal(int computedCalorieGoal) {
    this.computedCalorieGoal = computedCalorieGoal;
  }

  public Integer getCalorieGoalOverride() {
    return calorieGoalOverride;
  }

  public void setCalorieGoalOverride(Integer calorieGoalOverride) {
    this.calorieGoalOverride = calorieGoalOverride;
  }

  public ClampReason getCalorieGoalClamp() {
    return calorieGoalClamp;
  }

  public void setCalorieGoalClamp(ClampReason calorieGoalClamp) {
    this.calorieGoalClamp = calorieGoalClamp;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public void setCreatedAt(Instant createdAt) {
    this.createdAt = createdAt;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }

  public void setUpdatedAt(Instant updatedAt) {
    this.updatedAt = updatedAt;
  }
}
