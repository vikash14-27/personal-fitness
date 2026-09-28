package com.fitness.meal;

import java.time.LocalDate;

public record MealResponse(
    String id,
    LocalDate day,
    String description,
    int calories,
    double proteinG,
    double carbsG,
    double fatG) {}
