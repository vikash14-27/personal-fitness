package com.fitness.meal;

public record MealEstimateResponse(
    String description,
    int calories,
    double proteinG,
    double carbsG,
    double fatG) {}
