package com.fitness.profile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.fitness.calorie.CalorieBounds;
import com.fitness.calorie.CalorieCalculator;
import com.fitness.calorie.ClampReason;
import com.fitness.common.ApiException;
import com.fitness.user.ActivityLevel;
import com.fitness.user.Sex;
import com.fitness.user.User;
import com.fitness.user.UserRepository;
import java.math.BigDecimal;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.http.HttpStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ProfileServiceTest {

  @Mock
  private UserRepository users;

  private ProfileService profiles;

  @BeforeEach
  void setUp() {
    profiles = new ProfileService(users, new CalorieCalculator());
  }

  @Test
  void savingBioRecomputesTheGoal() {
    User user = existingUser();
    when(users.findById("user-1")).thenReturn(Optional.of(user));
    when(users.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

    ProfileResponse updated = profiles.update("user-1", request(new BigDecimal("90"), null));

    assertThat(updated.computedCalorieGoal()).isEqualTo(2914);
    assertThat(updated.activeCalorieGoal()).isEqualTo(2914);
    assertThat(updated.calorieGoalSource()).isEqualTo(CalorieGoalSource.COMPUTED);
    assertThat(updated.calorieGoalClamp()).isEqualTo(ClampReason.NONE);
    assertThat(updated.dailyAdjustment()).isZero();
  }

  @Test
  void manualOverrideIsActiveUntilCleared() {
    User user = existingUser();
    when(users.findById("user-1")).thenReturn(Optional.of(user));
    when(users.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

    ProfileResponse overridden = profiles.update("user-1", request(new BigDecimal("80"), 2200));
    assertThat(overridden.computedCalorieGoal()).isEqualTo(2364);
    assertThat(overridden.activeCalorieGoal()).isEqualTo(2200);
    assertThat(overridden.calorieGoalSource()).isEqualTo(CalorieGoalSource.OVERRIDE);
    assertThat(overridden.calorieGoalOverride()).isEqualTo(2200);

    ProfileResponse cleared = profiles.update("user-1", request(new BigDecimal("80"), null));
    assertThat(cleared.activeCalorieGoal()).isEqualTo(2364);
    assertThat(cleared.calorieGoalSource()).isEqualTo(CalorieGoalSource.COMPUTED);
    assertThat(cleared.calorieGoalOverride()).isNull();
  }

  @Test
  void bioChangeRecomputesEvenWhileAnOverrideIsSet() {
    User user = existingUser();
    user.setCalorieGoalOverride(2100);
    when(users.findById("user-1")).thenReturn(Optional.of(user));
    when(users.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

    ProfileResponse updated = profiles.update("user-1", request(new BigDecimal("90"), 2100));

    assertThat(updated.computedCalorieGoal()).isEqualTo(2914);
    assertThat(updated.activeCalorieGoal()).isEqualTo(2100);
    assertThat(updated.calorieGoalSource()).isEqualTo(CalorieGoalSource.OVERRIDE);
  }

  @Test
  void getReturnsTheStoredProfile() {
    User user = existingUser();
    when(users.findById("user-1")).thenReturn(Optional.of(user));

    ProfileResponse profile = profiles.get("user-1");

    assertThat(profile.email()).isEqualTo("ada@example.com");
    assertThat(profile.computedCalorieGoal()).isEqualTo(2364);
    assertThat(profile.activeCalorieGoal()).isEqualTo(2364);
    assertThat(profile.calorieGoalSource()).isEqualTo(CalorieGoalSource.COMPUTED);
    assertThat(profile.calorieGoalOverride()).isNull();
  }

  @Test
  void missingUserIsUnauthorized() {
    when(users.findById("missing")).thenReturn(Optional.empty());

    assertThatThrownBy(() -> profiles.get("missing"))
        .isInstanceOf(ApiException.class)
        .hasFieldOrPropertyWithValue("status", HttpStatus.UNAUTHORIZED)
        .hasFieldOrPropertyWithValue("error", "unauthorized");
    assertThatThrownBy(() -> profiles.update("missing", request(new BigDecimal("80"), null)))
        .isInstanceOf(ApiException.class)
        .hasFieldOrPropertyWithValue("error", "unauthorized");
  }

  @Test
  void overrideBoundsStayActiveAndKeepTheComputedGoal() {
    User user = existingUser();
    when(users.findById("user-1")).thenReturn(Optional.of(user));
    when(users.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

    ProfileResponse floor = profiles.update("user-1", request(new BigDecimal("80"), CalorieBounds.OVERRIDE_MIN_KCAL));
    assertThat(floor.calorieGoalOverride()).isEqualTo(1000);
    assertThat(floor.activeCalorieGoal()).isEqualTo(1000);
    assertThat(floor.computedCalorieGoal()).isEqualTo(2364);
    assertThat(floor.calorieGoalSource()).isEqualTo(CalorieGoalSource.OVERRIDE);

    ProfileResponse ceiling = profiles.update("user-1", request(new BigDecimal("80"), CalorieBounds.OVERRIDE_MAX_KCAL));
    assertThat(ceiling.calorieGoalOverride()).isEqualTo(5000);
    assertThat(ceiling.activeCalorieGoal()).isEqualTo(5000);
    assertThat(ceiling.computedCalorieGoal()).isEqualTo(2364);
    assertThat(ceiling.calorieGoalSource()).isEqualTo(CalorieGoalSource.OVERRIDE);
  }

  @Test
  void aggressiveLossStoresTheFloorWithoutDroppingAnOverride() {
    User user = existingUser();
    when(users.findById("user-1")).thenReturn(Optional.of(user));
    when(users.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

    ProfileResponse updated = profiles.update("user-1", new UpdateProfileRequest(
        80,
        Sex.MALE,
        new BigDecimal("160"),
        new BigDecimal("120"),
        new BigDecimal("70"),
        ActivityLevel.SEDENTARY,
        8,
        1800));

    assertThat(updated.computedCalorieGoal()).isEqualTo(CalorieBounds.MALE_FLOOR_KCAL);
    assertThat(updated.calorieGoalClamp()).isEqualTo(ClampReason.FLOOR);
    assertThat(updated.activeCalorieGoal()).isEqualTo(1800);
    assertThat(updated.calorieGoalSource()).isEqualTo(CalorieGoalSource.OVERRIDE);
  }

  private User existingUser() {
    User user = new User();
    user.setId("user-1");
    user.setEmail("ada@example.com");
    profiles.applyBio(
        user, 30, Sex.MALE, new BigDecimal("180"), new BigDecimal("90"), new BigDecimal("80"),
        ActivityLevel.MODERATELY_ACTIVE, 20, null);
    return user;
  }

  private static UpdateProfileRequest request(BigDecimal targetWeightKg, Integer override) {
    return new UpdateProfileRequest(
        30,
        Sex.MALE,
        new BigDecimal("180"),
        new BigDecimal("90"),
        targetWeightKg,
        ActivityLevel.MODERATELY_ACTIVE,
        20,
        override);
  }
}
