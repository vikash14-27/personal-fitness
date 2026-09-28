package com.fitness.meal;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fitness.config.NvidiaProperties;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.time.Duration;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
public class NvidiaNimClient implements NimCompletionClient {

  private static final Logger log = LoggerFactory.getLogger(NvidiaNimClient.class);

  private final NvidiaProperties nvidia;
  private final ObjectMapper objectMapper;
  private final RestClient restClient;

  public NvidiaNimClient(NvidiaProperties nvidia, ObjectMapper objectMapper) {
    this.nvidia = nvidia;
    this.objectMapper = objectMapper;
    this.restClient = buildClient(nvidia);
  }

  @Override
  public boolean isConfigured() {
    return nvidia.hasApiKey();
  }

  @Override
  public String complete(String systemPrompt, String userPrompt) {
    if (!nvidia.hasApiKey()) {
      throw new NimException("Meal estimates need an NVIDIA API key.");
    }
    if (restClient == null) {
      throw new NimException("The estimate service URL is invalid. Check NVIDIA_API_BASE_URL.");
    }

    ChatRequest body = new ChatRequest(
        nvidia.model(),
        0.2,
        0.7,
        512,
        false,
        List.of(
            new ChatMessage("system", systemPrompt),
            new ChatMessage("user", userPrompt)));

    try {
      String raw = restClient.post()
          .uri("chat/completions")
          .headers(headers -> {
            headers.setBearerAuth(nvidia.apiKey().trim());
            headers.setAccept(List.of(MediaType.APPLICATION_JSON));
          })
          .contentType(MediaType.APPLICATION_JSON)
          .body(body)
          .retrieve()
          .onStatus(HttpStatusCode::isError, (request, response) -> {
            int status = response.getStatusCode().value();
            try {
              response.getBody().readNBytes(2048);
            } catch (Exception ignored) {
              // The status is enough for the user-facing error.
            }
            log.warn("NVIDIA NIM returned HTTP {}", status);
            throw new NimException("The estimate service returned HTTP " + status + ".");
          })
          .body(String.class);
      return readContent(raw);
    } catch (NimException ex) {
      throw ex;
    } catch (ResourceAccessException ex) {
      if (causedBy(ex, SocketTimeoutException.class)) {
        throw new NimException("The estimate service took too long.", ex);
      }
      if (causedBy(ex, ConnectException.class)) {
        throw new NimException("The estimate service could not be reached.", ex);
      }
      log.warn("NVIDIA NIM request failed: {}", ex.getClass().getSimpleName());
      throw new NimException("The estimate service could not be reached.", ex);
    } catch (RestClientException ex) {
      NimException nim = findCause(ex, NimException.class);
      if (nim != null) {
        throw nim;
      }
      log.warn("NVIDIA NIM request failed: {}", ex.getClass().getSimpleName());
      throw new NimException("The estimate service returned an error.", ex);
    }
  }

  private String readContent(String raw) {
    if (raw == null || raw.isBlank()) {
      throw new NimException("The estimate service returned an empty response.");
    }
    try {
      JsonNode content = objectMapper.readTree(raw)
          .path("choices")
          .path(0)
          .path("message")
          .path("content");
      String text = textOf(content);
      if (text.isBlank()) {
        throw new NimException("The estimate service returned an empty response.");
      }
      return text;
    } catch (NimException ex) {
      throw ex;
    } catch (Exception ex) {
      throw new NimException("The estimate service returned an unreadable response.", ex);
    }
  }

  private static String textOf(JsonNode content) {
    if (content.isTextual()) {
      return content.asText();
    }
    if (!content.isArray()) {
      return "";
    }
    StringBuilder builder = new StringBuilder();
    for (JsonNode part : content) {
      if (part.isTextual()) {
        builder.append(part.asText());
      } else if (part.path("text").isTextual()) {
        builder.append(part.path("text").asText());
      }
    }
    return builder.toString();
  }

  /**
   * The base URL includes {@code /v1}. RestClient treats a URI that starts with
   * {@code /} as absolute from the host, which would drop that path, so the
   * client calls {@code chat/completions} against a base that ends in {@code /}.
   */
  static RestClient buildClient(NvidiaProperties nvidia) {
    String base = nvidia.baseUrl() == null ? "" : nvidia.baseUrl().trim();
    if (base.isEmpty()) {
      return null;
    }
    if (!base.endsWith("/")) {
      base = base + "/";
    }
    try {
      URI.create(base);
    } catch (IllegalArgumentException ex) {
      log.error("NVIDIA_API_BASE_URL is not a valid URL");
      return null;
    }
    SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
    factory.setConnectTimeout(Duration.ofSeconds(5));
    factory.setReadTimeout(Duration.ofSeconds(nvidia.timeoutSeconds()));
    return RestClient.builder().baseUrl(base).requestFactory(factory).build();
  }

  private static boolean causedBy(Throwable ex, Class<? extends Throwable> type) {
    return findCause(ex, type) != null;
  }

  private static <T extends Throwable> T findCause(Throwable ex, Class<T> type) {
    Throwable current = ex;
    while (current != null) {
      if (type.isInstance(current)) {
        return type.cast(current);
      }
      current = current.getCause();
    }
    return null;
  }

  private record ChatRequest(
      String model,
      double temperature,
      double top_p,
      int max_tokens,
      boolean stream,
      List<ChatMessage> messages) {}

  private record ChatMessage(String role, String content) {}
}
