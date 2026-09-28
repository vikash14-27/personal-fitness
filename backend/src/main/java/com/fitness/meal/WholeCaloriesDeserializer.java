package com.fitness.meal;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.exc.InvalidFormatException;
import java.io.IOException;
import java.math.BigDecimal;

/**
 * Meal calories are whole kilocalories. Jackson would otherwise coerce
 * {@code 380.5} to {@code 380} when the property type is {@code Integer}.
 */
public class WholeCaloriesDeserializer extends JsonDeserializer<Integer> {

  @Override
  public Integer deserialize(JsonParser parser, DeserializationContext context) throws IOException {
    JsonToken token = parser.currentToken();
    if (token == JsonToken.VALUE_NUMBER_INT) {
      return parser.getIntValue();
    }
    if (token == JsonToken.VALUE_NUMBER_FLOAT) {
      BigDecimal value = parser.getDecimalValue();
      if (value.stripTrailingZeros().scale() > 0) {
        throw InvalidFormatException.from(
            parser, "Calories must be a whole number", value, Integer.class);
      }
      try {
        return value.intValueExact();
      } catch (ArithmeticException ex) {
        throw InvalidFormatException.from(
            parser, "Calories must be a whole number", value, Integer.class);
      }
    }
    return (Integer) context.handleUnexpectedToken(Integer.class, parser);
  }
}
