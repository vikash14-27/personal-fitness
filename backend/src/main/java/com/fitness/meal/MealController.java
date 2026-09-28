package com.fitness.meal;

import com.fitness.common.CurrentUser;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/meals")
public class MealController {

  private final MealEstimateService estimates;
  private final MealService meals;

  public MealController(MealEstimateService estimates, MealService meals) {
    this.estimates = estimates;
    this.meals = meals;
  }

  @PostMapping("/estimate")
  public MealEstimateResponse estimate(@Valid @RequestBody EstimateMealRequest request) {
    return estimates.estimate(CurrentUser.id(), request.text());
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public MealResponse create(@Valid @RequestBody SaveMealRequest request) {
    return meals.create(CurrentUser.id(), request);
  }

  @PutMapping("/{id}")
  public MealResponse update(@PathVariable String id, @Valid @RequestBody SaveMealRequest request) {
    return meals.update(CurrentUser.id(), id, request);
  }

  @DeleteMapping("/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void delete(@PathVariable String id) {
    meals.delete(CurrentUser.id(), id);
  }
}
