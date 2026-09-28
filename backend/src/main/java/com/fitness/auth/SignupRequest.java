package com.fitness.auth;

import com.fitness.calorie.CalorieBounds;
import com.fitness.user.ActivityLevel;
import com.fitness.user.Sex;
import java.math.BigDecimal;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record SignupRequest(
    @NotBlank(message = "Email is required")
    @Email(message = "Enter a valid email address")
    @Size(max = 254, message = "Email is too long")
    String email,

    @NotBlank(message = "Password is required")
    @Size(min = 8, max = 72, message = "Password must be 8–72 characters")
    String password,

    @NotNull(message = "Age is required")
    @Min(value = CalorieBounds.MIN_AGE, message = "Age must be between 16 and 100")
    @Max(value = CalorieBounds.MAX_AGE, message = "Age must be between 16 and 100")
    Integer age,

    @NotNull(message = "Sex is required")
    Sex sex,

    @NotNull(message = "Height is required")
    @DecimalMin(value = CalorieBounds.MIN_HEIGHT_CM, message = "Height must be between 120 and 250 cm")
    @DecimalMax(value = CalorieBounds.MAX_HEIGHT_CM, message = "Height must be between 120 and 250 cm")
    @Digits(integer = 3, fraction = 1, message = "Height can have at most one decimal place")
    BigDecimal heightCm,

    @NotNull(message = "Current weight is required")
    @DecimalMin(value = CalorieBounds.MIN_WEIGHT_KG, message = "Current weight must be between 30 and 300 kg")
    @DecimalMax(value = CalorieBounds.MAX_WEIGHT_KG, message = "Current weight must be between 30 and 300 kg")
    @Digits(integer = 3, fraction = 1, message = "Current weight can have at most one decimal place")
    BigDecimal currentWeightKg,

    @NotNull(message = "Target weight is required")
    @DecimalMin(value = CalorieBounds.MIN_WEIGHT_KG, message = "Target weight must be between 30 and 300 kg")
    @DecimalMax(value = CalorieBounds.MAX_WEIGHT_KG, message = "Target weight must be between 30 and 300 kg")
    @Digits(integer = 3, fraction = 1, message = "Target weight can have at most one decimal place")
    BigDecimal targetWeightKg,

    @NotNull(message = "Activity level is required")
    ActivityLevel activityLevel,

    @NotNull(message = "Timeline is required")
    @Min(value = CalorieBounds.MIN_TIMELINE_WEEKS, message = "Timeline must be between 1 and 104 weeks")
    @Max(value = CalorieBounds.MAX_TIMELINE_WEEKS, message = "Timeline must be between 1 and 104 weeks")
    Integer timelineWeeks) {}
