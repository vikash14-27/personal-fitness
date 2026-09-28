package com.fitness.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.nvidia")
public record NvidiaProperties(String apiKey, String baseUrl, String model, int timeoutSeconds) {

  public NvidiaProperties {
    if (apiKey == null) {
      apiKey = "";
    }
    if (baseUrl == null || baseUrl.isBlank()) {
      baseUrl = NvidiaDefaults.BASE_URL;
    }
    if (model == null || model.isBlank()) {
      model = NvidiaDefaults.MODEL;
    }
    if (timeoutSeconds <= 0) {
      timeoutSeconds = NvidiaDefaults.TIMEOUT_SECONDS;
    }
  }

  public boolean hasApiKey() {
    return !apiKey.isBlank();
  }
}
