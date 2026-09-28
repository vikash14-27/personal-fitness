package com.fitness.meal;

import com.fitness.common.CurrentUser;
import java.time.LocalDate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/days")
public class DayController {

  private final MealService meals;

  public DayController(MealService meals) {
    this.meals = meals;
  }

  @GetMapping("/{day}")
  public DayResponse get(@PathVariable LocalDate day) {
    return meals.day(CurrentUser.id(), day);
  }
}
