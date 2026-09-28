package com.fitness.meal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fitness.config.NvidiaDefaults;
import com.fitness.config.NvidiaProperties;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class NvidiaNimClientTest {

  private HttpServer server;

  @AfterEach
  void stop() {
    if (server != null) {
      server.stop(0);
    }
  }

  @Test
  void postsToTheVersionedChatEndpointWithoutPuttingTheKeyInTheBody() throws Exception {
    AtomicReference<String> path = new AtomicReference<>();
    AtomicReference<String> authorization = new AtomicReference<>();
    AtomicReference<String> body = new AtomicReference<>();
    server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext("/chat/completions", exchange -> exchange.sendResponseHeaders(500, -1));
    server.createContext("/v1/chat/completions", exchange -> {
      path.set(exchange.getRequestURI().getPath());
      authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
      body.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
      byte[] response = """
          {"choices":[{"message":{"content":"{\\"description\\":\\"Oatmeal\\",\\"calories\\":150,\\"proteinG\\":5,\\"carbsG\\":27,\\"fatG\\":3}"}}]}
          """.getBytes(StandardCharsets.UTF_8);
      exchange.getResponseHeaders().add("Content-Type", "application/json");
      exchange.sendResponseHeaders(200, response.length);
      exchange.getResponseBody().write(response);
      exchange.close();
    });
    server.start();

    String secret = "nvapi-test-secret";
    NvidiaNimClient client = client("http://127.0.0.1:" + server.getAddress().getPort() + "/v1", secret);

    String content = client.complete("system", "oatmeal");

    assertThat(content).contains("Oatmeal");
    assertThat(path.get()).isEqualTo("/v1/chat/completions");
    assertThat(authorization.get()).isEqualTo("Bearer " + secret);
    assertThat(body.get()).doesNotContain(secret);
    JsonNode sent = new ObjectMapper().readTree(body.get());
    assertThat(sent.path("model").asText()).isEqualTo("meta/llama-3.3-70b-instruct");
    assertThat(sent.path("stream").asBoolean()).isFalse();
    assertThat(sent.path("messages").get(1).path("content").asText()).isEqualTo("oatmeal");
  }

  @Test
  void httpErrorsDoNotExposeTheResponseBody() throws Exception {
    server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext("/v1/chat/completions", exchange -> {
      byte[] response = "{\"error\":\"nvapi-test-secret leaked\"}".getBytes(StandardCharsets.UTF_8);
      exchange.getResponseHeaders().add("Content-Type", "application/json");
      exchange.sendResponseHeaders(429, response.length);
      exchange.getResponseBody().write(response);
      exchange.close();
    });
    server.start();

    NvidiaNimClient client = client(
        "http://127.0.0.1:" + server.getAddress().getPort() + "/v1",
        "nvapi-test-secret");

    assertThatThrownBy(() -> client.complete("system", "soup"))
        .isInstanceOf(NimException.class)
        .hasMessageContaining("HTTP 429")
        .hasMessageNotContaining("nvapi-test-secret");
  }

  @Test
  void http500DoesNotLeakTheUpstreamBody() throws Exception {
    server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext("/v1/chat/completions", exchange -> {
      byte[] response = "{\"error\":\"test-nim-key leaked upstream\"}".getBytes(StandardCharsets.UTF_8);
      exchange.getResponseHeaders().add("Content-Type", "application/json");
      exchange.sendResponseHeaders(500, response.length);
      exchange.getResponseBody().write(response);
      exchange.close();
    });
    server.start();

    NvidiaNimClient client = client(
        "http://127.0.0.1:" + server.getAddress().getPort() + "/v1",
        "test-nim-key");

    assertThatThrownBy(() -> client.complete("system", "soup"))
        .isInstanceOf(NimException.class)
        .hasMessageContaining("HTTP 500")
        .hasMessageNotContaining("test-nim-key")
        .hasMessageNotContaining("leaked");
  }

  @Test
  void aReadTimeoutIsReportedWithoutCallingTheLiveNimHost() throws Exception {
    server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext("/v1/chat/completions", exchange -> {
      exchange.getRequestBody().readAllBytes();
      try {
        Thread.sleep(2_000);
      } catch (InterruptedException ex) {
        Thread.currentThread().interrupt();
      }
      exchange.sendResponseHeaders(204, -1);
      exchange.close();
    });
    server.start();

    NvidiaNimClient client = client(
        "http://127.0.0.1:" + server.getAddress().getPort() + "/v1",
        "test-nim-key",
        1);

    assertThatThrownBy(() -> client.complete("system", "soup"))
        .isInstanceOf(NimException.class)
        .hasMessageContaining("took too long")
        .hasMessageNotContaining("integrate.api.nvidia.com");
  }

  @Test
  void anEmptyBodyIsUnusable() throws Exception {
    server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext("/v1/chat/completions", exchange -> {
      exchange.getRequestBody().readAllBytes();
      exchange.sendResponseHeaders(200, -1);
      exchange.close();
    });
    server.start();

    NvidiaNimClient client = client(
        "http://127.0.0.1:" + server.getAddress().getPort() + "/v1",
        "test-nim-key");

    assertThatThrownBy(() -> client.complete("system", "soup"))
        .isInstanceOf(NimException.class)
        .hasMessageContaining("empty response");
  }

  @Test
  void defaultsTargetLlamaOnTheVersionedNimChatApi() throws Exception {
    assertThat(NvidiaDefaults.MODEL).isEqualTo("meta/llama-3.3-70b-instruct");
    assertThat(NvidiaDefaults.BASE_URL).isEqualTo("https://integrate.api.nvidia.com/v1");

    NvidiaProperties defaults = new NvidiaProperties(null, " ", " ", 0);
    assertThat(defaults.hasApiKey()).isFalse();
    assertThat(defaults.model()).isEqualTo("meta/llama-3.3-70b-instruct");
    assertThat(defaults.baseUrl()).isEqualTo("https://integrate.api.nvidia.com/v1");
    assertThat(defaults.baseUrl() + "/chat/completions")
        .isEqualTo("https://integrate.api.nvidia.com/v1/chat/completions");
    assertThat(NvidiaNimClient.buildClient(defaults)).isNotNull();

    String yaml = Files.readString(Path.of("src/main/resources/application.yml"));
    assertThat(yaml).contains("https://integrate.api.nvidia.com/v1");
    assertThat(yaml).contains("meta/llama-3.3-70b-instruct");
  }

  @Test
  void aBlankKeyDoesNotOpenAConnection() throws Exception {
    AtomicInteger hits = new AtomicInteger();
    server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext("/v1/chat/completions", exchange -> {
      hits.incrementAndGet();
      exchange.sendResponseHeaders(200, -1);
    });
    server.start();

    NvidiaNimClient client = client(
        "http://127.0.0.1:" + server.getAddress().getPort() + "/v1",
        "  ");

    assertThat(client.isConfigured()).isFalse();
    assertThatThrownBy(() -> client.complete("system", "soup")).isInstanceOf(NimException.class);
    assertThat(hits.get()).isZero();
  }

  private static NvidiaNimClient client(String baseUrl, String apiKey) {
    return client(baseUrl, apiKey, 5);
  }

  private static NvidiaNimClient client(String baseUrl, String apiKey, int timeoutSeconds) {
    return new NvidiaNimClient(
        new NvidiaProperties(apiKey, baseUrl, " ", timeoutSeconds),
        new ObjectMapper());
  }
}
