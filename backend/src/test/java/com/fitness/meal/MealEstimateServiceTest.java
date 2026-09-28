package com.fitness.meal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fitness.common.ApiException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

@ExtendWith(MockitoExtension.class)
class MealEstimateServiceTest {

  @Mock
  private NimCompletionClient nim;

  private MealEstimateService estimates;

  @BeforeEach
  void setUp() {
    estimates = new MealEstimateService(nim, new MealEstimateParser(new ObjectMapper()));
  }

  @Test
  void missingKeyDoesNotCallNim() {
    when(nim.isConfigured()).thenReturn(false);

    assertThatThrownBy(() -> estimates.estimate("user-1", "two eggs"))
        .isInstanceOf(ApiException.class)
        .hasMessageContaining("NVIDIA_API_KEY")
        .extracting("status", "error")
        .containsExactly(HttpStatus.SERVICE_UNAVAILABLE, "estimate_unavailable");

    verify(nim, never()).complete(anyString(), anyString());
  }

  @Test
  void apiErrorsStayActionable() {
    when(nim.isConfigured()).thenReturn(true);
    when(nim.complete(anyString(), anyString()))
        .thenThrow(new NimException("The estimate service returned HTTP 503."));

    assertThatThrownBy(() -> estimates.estimate("user-1", "two eggs"))
        .isInstanceOf(ApiException.class)
        .hasMessageContaining("HTTP 503")
        .hasMessageContaining("yourself")
        .extracting("status", "error")
        .containsExactly(HttpStatus.BAD_GATEWAY, "estimate_failed");
  }

  @Test
  void http500FromNimIsBadGateway() {
    when(nim.isConfigured()).thenReturn(true);
    when(nim.complete(anyString(), anyString()))
        .thenThrow(new NimException("The estimate service returned HTTP 500."));

    assertThatThrownBy(() -> estimates.estimate("user-1", "two eggs"))
        .isInstanceOf(ApiException.class)
        .hasMessageContaining("HTTP 500")
        .hasMessageNotContaining("nvapi")
        .extracting("status", "error")
        .containsExactly(HttpStatus.BAD_GATEWAY, "estimate_failed");
  }

  @Test
  void timeoutFromNimIsBadGateway() {
    when(nim.isConfigured()).thenReturn(true);
    when(nim.complete(anyString(), anyString()))
        .thenThrow(new NimException("The estimate service took too long."));

    assertThatThrownBy(() -> estimates.estimate("user-1", "two eggs"))
        .isInstanceOf(ApiException.class)
        .hasMessageContaining("too long")
        .extracting("status", "error")
        .containsExactly(HttpStatus.BAD_GATEWAY, "estimate_failed");
  }

  @Test
  void unusableModelOutputDoesNotCrash() {
    when(nim.isConfigured()).thenReturn(true);
    when(nim.complete(anyString(), anyString())).thenReturn("I can't help with that.");

    assertThatThrownBy(() -> estimates.estimate("user-1", "two eggs"))
        .isInstanceOf(ApiException.class)
        .hasMessageContaining("usable form")
        .extracting("status", "error")
        .containsExactly(HttpStatus.BAD_GATEWAY, "estimate_failed");
  }

  @Test
  void sendsTheCollapsedMealTextToNim() {
    when(nim.isConfigured()).thenReturn(true);
    when(nim.complete(anyString(), anyString())).thenReturn(
        "{\"description\":\"Two eggs\",\"calories\":156,\"proteinG\":12.4,\"carbsG\":1.2,\"fatG\":10.6}");

    estimates.estimate("user-1", "  two   eggs  ");

    ArgumentCaptor<String> system = ArgumentCaptor.forClass(String.class);
    ArgumentCaptor<String> user = ArgumentCaptor.forClass(String.class);
    verify(nim).complete(system.capture(), user.capture());
    assertThat(system.getValue()).contains("JSON");
    assertThat(user.getValue()).contains("two eggs");
    assertThat(user.getValue()).doesNotContain("  two");
  }

  @Test
  void returnsParsedNutrition() {
    when(nim.isConfigured()).thenReturn(true);
    when(nim.complete(anyString(), anyString())).thenReturn(
        "{\"description\":\"Two eggs\",\"calories\":156,\"proteinG\":12.4,\"carbsG\":1.2,\"fatG\":10.6}");

    MealEstimateResponse estimate = estimates.estimate("user-1", "  two   eggs  ");

    assertThat(estimate.description()).isEqualTo("Two eggs");
    assertThat(estimate.calories()).isEqualTo(156);
    assertThat(estimate.proteinG()).isEqualTo(12.4);
  }
}
