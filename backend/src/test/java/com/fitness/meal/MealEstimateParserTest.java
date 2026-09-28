package com.fitness.meal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class MealEstimateParserTest {

  private MealEstimateParser parser;

  @BeforeEach
  void setUp() {
    parser = new MealEstimateParser(new ObjectMapper());
  }

  @Test
  void parsesAJsonObject() {
    MealEstimateResponse estimate = parser.parse(
        "two eggs",
        """
        {"description":"Two eggs","calories":156,"proteinG":12.4,"carbsG":1.2,"fatG":10.6}
        """);

    assertThat(estimate.description()).isEqualTo("Two eggs");
    assertThat(estimate.calories()).isEqualTo(156);
    assertThat(estimate.proteinG()).isEqualTo(12.4);
    assertThat(estimate.carbsG()).isEqualTo(1.2);
    assertThat(estimate.fatG()).isEqualTo(10.6);
  }

  @Test
  void parsesJsonWrappedInMarkdownAndProse() {
    MealEstimateResponse estimate = parser.parse(
        "oatmeal",
        """
        Here is the estimate:
        ```json
        {"description":"Oatmeal","calories":150,"proteinG":"5","carbsG":27,"fatG":3.04}
        ```
        """);

    assertThat(estimate.description()).isEqualTo("Oatmeal");
    assertThat(estimate.calories()).isEqualTo(150);
    assertThat(estimate.proteinG()).isEqualTo(5.0);
    assertThat(estimate.fatG()).isEqualTo(3.0);
  }

  @Test
  void keepsBracesThatAppearInsideStrings() {
    String raw = "{\"description\":\"bowl of oats}\",\"calories\":150,\"proteinG\":5,\"carbsG\":27,\"fatG\":3}";

    assertThat(MealEstimateParser.extractJsonObject("note " + raw)).isEqualTo(raw);
    assertThat(parser.parse("oats", raw).description()).isEqualTo("bowl of oats}");
  }

  @Test
  void acceptsAliasFieldsAndFallsBackToTheOriginalText() {
    MealEstimateResponse estimate = parser.parse(
        "toast with butter",
        "{\"calories\":180,\"protein\":4,\"carbohydrates\":20,\"fat\":9}");

    assertThat(estimate.description()).isEqualTo("toast with butter");
    assertThat(estimate.calories()).isEqualTo(180);
    assertThat(estimate.proteinG()).isEqualTo(4.0);
    assertThat(estimate.carbsG()).isEqualTo(20.0);
    assertThat(estimate.fatG()).isEqualTo(9.0);
  }

  @Test
  void rejectsGarbageAndOutOfRangeCalories() {
    assertThatThrownBy(() -> parser.parse("soup", "not json"))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> parser.parse(
            "soup",
            "{\"description\":\"Soup\",\"calories\":50000,\"proteinG\":1,\"carbsG\":1,\"fatG\":1}"))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void rejectsFractionalCaloriesAndMacrosOutsideRange() {
    assertThatThrownBy(() -> parser.parse(
            "soup",
            "{\"description\":\"Soup\",\"calories\":12.5,\"proteinG\":1,\"carbsG\":1,\"fatG\":1}"))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> parser.parse(
            "soup",
            "{\"description\":\"Soup\",\"calories\":12,\"proteinG\":-0.1,\"carbsG\":1,\"fatG\":1}"))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> parser.parse(
            "soup",
            "{\"description\":\"Soup\",\"calories\":12,\"proteinG\":1000.06,\"carbsG\":1,\"fatG\":1}"))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void acceptsWholeCaloriesAndMacrosOnTheInclusiveBounds() {
    MealEstimateResponse estimate = parser.parse(
        "water",
        "{\"calories\":0,\"proteinG\":0,\"carbsG\":1000,\"fatG\":1000.04}");

    assertThat(estimate.calories()).isZero();
    assertThat(estimate.proteinG()).isEqualTo(0.0);
    assertThat(estimate.carbsG()).isEqualTo(1000.0);
    assertThat(estimate.fatG()).isEqualTo(1000.0);

    assertThat(parser.parse(
            "feast",
            "{\"description\":\"Feast\",\"calories\":10000,\"proteinG\":1,\"carbsG\":1,\"fatG\":1}")
        .calories()).isEqualTo(10000);
  }
}
