package com.fitness.meal;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record EstimateMealRequest(
    @NotBlank(message = "Describe the meal")
    @Size(max = MealBounds.MAX_ESTIMATE_TEXT, message = "Meal description must be 500 characters or fewer")
    String text) {}
