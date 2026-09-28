package com.fitness.meal;

import java.lang.reflect.Proxy;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Stores meals in a map so API tests can exercise {@link MealService} without MongoDB.
 * Only the methods the service calls are implemented.
 */
public final class InMemoryMealRepository {

  private InMemoryMealRepository() {}

  public static MealRepository create() {
    Map<String, Meal> store = new ConcurrentHashMap<>();
    AtomicInteger ids = new AtomicInteger();
    return (MealRepository) Proxy.newProxyInstance(
        MealRepository.class.getClassLoader(),
        new Class<?>[] {MealRepository.class},
        (proxy, method, args) -> {
          if (method.getDeclaringClass() == Object.class) {
            return switch (method.getName()) {
              case "toString" -> "InMemoryMealRepository";
              case "hashCode" -> System.identityHashCode(proxy);
              case "equals" -> proxy == args[0];
              default -> throw new UnsupportedOperationException(method.getName());
            };
          }
          return switch (method.getName()) {
            case "save" -> {
              Meal meal = (Meal) args[0];
              if (meal.getId() == null || meal.getId().isBlank()) {
                meal.setId("meal-" + ids.incrementAndGet());
              }
              store.put(meal.getId(), meal);
              yield meal;
            }
            case "findByIdAndUserId" -> {
              Meal meal = store.get(args[0]);
              if (meal != null && meal.getUserId().equals(args[1])) {
                yield Optional.of(meal);
              }
              yield Optional.empty();
            }
            case "findByUserIdAndDayOrderByCreatedAtAsc" -> {
              String userId = (String) args[0];
              LocalDate day = (LocalDate) args[1];
              yield store.values().stream()
                  .filter(meal -> userId.equals(meal.getUserId()) && day.equals(meal.getDay()))
                  .sorted(Comparator.comparing(Meal::getCreatedAt).thenComparing(Meal::getId))
                  .toList();
            }
            case "delete" -> {
              if (args.length == 1 && args[0] instanceof Meal meal) {
                store.remove(meal.getId());
                yield null;
              }
              throw new UnsupportedOperationException("delete");
            }
            default -> throw new UnsupportedOperationException(method.getName());
          };
        });
  }
}
