package com.fitness.meal;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;

public record SaveMealRequest(
    @NotNull(message = "Day is required")
    LocalDate day,

    @NotBlank(message = "Name the meal")
    @Size(max = MealBounds.MAX_DESCRIPTION, message = "Use 300 characters or fewer")
    String description,

    @NotNull(message = "Calories are required")
    @Min(value = MealBounds.MIN_CALORIES, message = "Calories must be between 0 and 10000")
    @Max(value = MealBounds.MAX_CALORIES, message = "Calories must be between 0 and 10000")
    @JsonDeserialize(using = WholeCaloriesDeserializer.class)
    Integer calories,

    @NotNull(message = "Protein is required")
    @DecimalMin(value = MealBounds.MIN_MACRO, message = "Protein must be between 0 and 1000 g")
    @DecimalMax(value = MealBounds.MAX_MACRO, message = "Protein must be between 0 and 1000 g")
    @Digits(integer = 4, fraction = 1, message = "Protein can have at most one decimal place")
    BigDecimal proteinG,

    @NotNull(message = "Carbs are required")
    @DecimalMin(value = MealBounds.MIN_MACRO, message = "Carbs must be between 0 and 1000 g")
    @DecimalMax(value = MealBounds.MAX_MACRO, message = "Carbs must be between 0 and 1000 g")
    @Digits(integer = 4, fraction = 1, message = "Carbs can have at most one decimal place")
    BigDecimal carbsG,

    @NotNull(message = "Fat is required")
    @DecimalMin(value = MealBounds.MIN_MACRO, message = "Fat must be between 0 and 1000 g")
    @DecimalMax(value = MealBounds.MAX_MACRO, message = "Fat must be between 0 and 1000 g")
    @Digits(integer = 4, fraction = 1, message = "Fat can have at most one decimal place")
    BigDecimal fatG) {}
