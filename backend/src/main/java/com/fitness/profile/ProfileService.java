package com.fitness.profile;

import com.fitness.calorie.CalorieCalculator;
import com.fitness.calorie.CalorieGoal;
import com.fitness.common.ApiException;
import com.fitness.user.ActivityLevel;
import com.fitness.user.Sex;
import com.fitness.user.User;
import com.fitness.user.UserRepository;
import java.math.BigDecimal;
import java.time.Instant;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class ProfileService {

  private final UserRepository users;
  private final CalorieCalculator calculator;

  public ProfileService(UserRepository users, CalorieCalculator calculator) {
    this.users = users;
    this.calculator = calculator;
  }

  public ProfileResponse get(String userId) {
    return toResponse(requireUser(userId));
  }

  public ProfileResponse update(String userId, UpdateProfileRequest request) {
    User user = requireUser(userId);
    applyBio(
        user,
        request.age(),
        request.sex(),
        request.heightCm(),
        request.currentWeightKg(),
        request.targetWeightKg(),
        request.activityLevel(),
        request.timelineWeeks(),
        request.calorieGoalOverride());
    return toResponse(users.save(user));
  }

  public void applyBio(
      User user,
      int age,
      Sex sex,
      BigDecimal heightCm,
      BigDecimal currentWeightKg,
      BigDecimal targetWeightKg,
      ActivityLevel activityLevel,
      int timelineWeeks,
      Integer calorieGoalOverride) {
    CalorieGoal goal = calculator.calculate(
        sex, age, heightCm, currentWeightKg, targetWeightKg, activityLevel, timelineWeeks);
    user.setAge(age);
    user.setSex(sex);
    user.setHeightCm(heightCm.doubleValue());
    user.setCurrentWeightKg(currentWeightKg.doubleValue());
    user.setTargetWeightKg(targetWeightKg.doubleValue());
    user.setActivityLevel(activityLevel);
    user.setTimelineWeeks(timelineWeeks);
    user.setComputedCalorieGoal(goal.computedGoal());
    user.setCalorieGoalClamp(goal.clamp());
    user.setCalorieGoalOverride(calorieGoalOverride);
    user.setUpdatedAt(Instant.now());
  }

  public ProfileResponse toResponse(User user) {
    CalorieGoal fresh = calculator.calculate(
        user.getSex(),
        user.getAge(),
        BigDecimal.valueOf(user.getHeightCm()),
        BigDecimal.valueOf(user.getCurrentWeightKg()),
        BigDecimal.valueOf(user.getTargetWeightKg()),
        user.getActivityLevel(),
        user.getTimelineWeeks());
    Integer override = user.getCalorieGoalOverride();
    boolean overridden = override != null;
    return new ProfileResponse(
        user.getId(),
        user.getEmail(),
        user.getAge(),
        user.getSex(),
        user.getHeightCm(),
        user.getCurrentWeightKg(),
        user.getTargetWeightKg(),
        user.getActivityLevel(),
        user.getTimelineWeeks(),
        fresh.bmr(),
        fresh.tdee(),
        fresh.activityMultiplier(),
        fresh.dailyAdjustment(),
        user.getComputedCalorieGoal(),
        override,
        overridden ? override : user.getComputedCalorieGoal(),
        overridden ? CalorieGoalSource.OVERRIDE : CalorieGoalSource.COMPUTED,
        user.getCalorieGoalClamp());
  }

  private User requireUser(String userId) {
    return users.findById(userId)
        .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "unauthorized", "Sign in required."));
  }
}
