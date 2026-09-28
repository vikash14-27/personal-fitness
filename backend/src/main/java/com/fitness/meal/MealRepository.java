package com.fitness.meal;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface MealRepository extends MongoRepository<Meal, String> {

  List<Meal> findByUserIdAndDayOrderByCreatedAtAsc(String userId, LocalDate day);

  Optional<Meal> findByIdAndUserId(String id, String userId);
}
