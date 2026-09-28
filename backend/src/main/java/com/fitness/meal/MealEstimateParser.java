package com.fitness.meal;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.math.RoundingMode;
import org.springframework.stereotype.Component;

@Component
public class MealEstimateParser {

  private final ObjectMapper objectMapper;

  public MealEstimateParser(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  public MealEstimateResponse parse(String fallbackDescription, String modelContent) {
    try {
      JsonNode node = objectMapper.readTree(extractJsonObject(modelContent));
      String description = description(node, fallbackDescription);
      int calories = calories(node);
      double proteinG = macro(node, "proteinG", "protein_g", "protein");
      double carbsG = macro(node, "carbsG", "carbs_g", "carbohydrates", "carbs");
      double fatG = macro(node, "fatG", "fat_g", "fat");
      return new MealEstimateResponse(description, calories, proteinG, carbsG, fatG);
    } catch (JsonProcessingException ex) {
      throw new IllegalArgumentException("Unreadable estimate", ex);
    }
  }

  static String extractJsonObject(String raw) {
    if (raw == null) {
      throw new IllegalArgumentException("empty");
    }
    String trimmed = raw.trim();
    int start = trimmed.indexOf('{');
    if (start < 0) {
      throw new IllegalArgumentException("no object");
    }
    boolean inString = false;
    boolean escape = false;
    int depth = 0;
    for (int i = start; i < trimmed.length(); i++) {
      char current = trimmed.charAt(i);
      if (inString) {
        if (escape) {
          escape = false;
        } else if (current == '\\') {
          escape = true;
        } else if (current == '"') {
          inString = false;
        }
        continue;
      }
      if (current == '"') {
        inString = true;
      } else if (current == '{') {
        depth++;
      } else if (current == '}') {
        depth--;
        if (depth == 0) {
          return trimmed.substring(start, i + 1);
        }
      }
    }
    throw new IllegalArgumentException("unterminated");
  }

  private static String description(JsonNode node, String fallback) {
    String value = node.path("description").asText("").trim();
    if (value.isBlank()) {
      value = fallback == null ? "" : fallback.trim();
    }
    value = value.replaceAll("[\\p{Cntrl}]", " ").replaceAll("\\s+", " ").trim();
    if (value.length() > MealBounds.MAX_DESCRIPTION) {
      value = value.substring(0, MealBounds.MAX_DESCRIPTION).trim();
    }
    if (value.isBlank()) {
      throw new IllegalArgumentException("description");
    }
    return value;
  }

  private static int calories(JsonNode node) {
    BigDecimal whole = requiredNumber(node, "calories", "kcal").stripTrailingZeros();
    if (whole.scale() > 0) {
      throw new IllegalArgumentException("calories");
    }
    try {
      int kcal = whole.intValueExact();
      if (kcal < MealBounds.MIN_CALORIES || kcal > MealBounds.MAX_CALORIES) {
        throw new IllegalArgumentException("calories");
      }
      return kcal;
    } catch (ArithmeticException ex) {
      throw new IllegalArgumentException("calories", ex);
    }
  }

  private static double macro(JsonNode node, String... names) {
    BigDecimal value = requiredNumber(node, names);
    if (value.signum() < 0) {
      throw new IllegalArgumentException("macro");
    }
    BigDecimal rounded = value.setScale(1, RoundingMode.HALF_UP);
    if (rounded.compareTo(new BigDecimal(MealBounds.MAX_MACRO)) > 0) {
      throw new IllegalArgumentException("macro");
    }
    return rounded.doubleValue();
  }

  private static BigDecimal requiredNumber(JsonNode node, String... names) {
    JsonNode value = null;
    for (String name : names) {
      JsonNode candidate = node.get(name);
      if (candidate != null && !candidate.isNull()) {
        value = candidate;
        break;
      }
    }
    if (value == null) {
      throw new IllegalArgumentException("missing");
    }
    if (value.isNumber()) {
      return value.decimalValue();
    }
    if (value.isTextual()) {
      try {
        return new BigDecimal(value.asText().trim());
      } catch (NumberFormatException ex) {
        throw new IllegalArgumentException("number", ex);
      }
    }
    throw new IllegalArgumentException("number");
  }
}
