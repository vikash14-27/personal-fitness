package com.fitness.meal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fitness.auth.JwtAuthFilter;
import com.fitness.auth.JwtService;
import com.fitness.calorie.ClampReason;
import com.fitness.common.ApiException;
import com.fitness.common.GlobalExceptionHandler;
import com.fitness.config.AppProperties;
import com.fitness.config.NvidiaDefaults;
import com.fitness.config.NvidiaProperties;
import com.fitness.profile.CalorieGoalSource;
import com.fitness.profile.ProfileResponse;
import com.fitness.profile.ProfileService;
import com.fitness.user.ActivityLevel;
import com.fitness.user.Sex;
import com.fitness.user.User;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

/**
 * Meal HTTP API wired to the real estimate and day services.
 * NIM is a local stub, so these tests do not call integrate.api.nvidia.com or MongoDB.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class MealApiIntegrationTest {

  private static final String NIM_KEY = "test-nim-key";

  private enum NimStub {
    OK, HTTP_500, PROSE, FRACTIONAL, TIMEOUT
  }

  @Mock
  private ProfileService profiles;

  private final ObjectMapper mapper = new ObjectMapper()
      .registerModule(new JavaTimeModule())
      .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
  private final Map<String, ProfileResponse> profilesById = new ConcurrentHashMap<>();
  private final Map<String, String> tokens = new ConcurrentHashMap<>();
  private final AtomicInteger nimHits = new AtomicInteger();
  private final AtomicInteger decoyHits = new AtomicInteger();
  private final AtomicReference<String> requestPath = new AtomicReference<>();
  private final AtomicReference<String> requestQuery = new AtomicReference<>();
  private final AtomicReference<String> authorization = new AtomicReference<>();
  private final AtomicReference<String> requestBody = new AtomicReference<>();

  private HttpServer server;
  private volatile NimStub stub = NimStub.OK;
  private MealRepository meals;
  private JwtService jwt;
  private MockMvc api;
  private MockMvc unconfigured;

  @BeforeEach
  void setUp() throws Exception {
    SecurityContextHolder.clearContext();
    profilesById.put("user-1", profile("user-1", 2000, null, CalorieGoalSource.COMPUTED));
    profilesById.put("user-2", profile("user-2", 1800, 1800, CalorieGoalSource.OVERRIDE));
    when(profiles.get(anyString())).thenAnswer(invocation -> {
      String userId = invocation.getArgument(0);
      ProfileResponse profile = profilesById.get(userId);
      if (profile == null) {
        throw new ApiException(HttpStatus.NOT_FOUND, "user_not_found", "User not found.");
      }
      return profile;
    });

    server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext("/chat/completions", exchange -> {
      decoyHits.incrementAndGet();
      exchange.sendResponseHeaders(500, -1);
      exchange.close();
    });
    server.createContext("/v1/chat/completions", this::handleNim);
    server.start();

    String baseUrl = "http://127.0.0.1:" + server.getAddress().getPort() + "/v1";
    meals = InMemoryMealRepository.create();
    jwt = new JwtService(new AppProperties(
        new AppProperties.Jwt("dev-only-jwt-secret-change-me-use-32b-min!!", 3_600_000),
        List.of("http://localhost:5173")));
    NvidiaNimClient configured = new NvidiaNimClient(
        new NvidiaProperties(NIM_KEY, baseUrl, " ", 1),
        mapper);
    NvidiaNimClient missingKey = new NvidiaNimClient(
        new NvidiaProperties(" ", baseUrl, " ", 1),
        mapper);
    api = mockMvc(configured);
    unconfigured = mockMvc(missingKey);
  }

  @AfterEach
  void tearDown() {
    if (server != null) {
      server.stop(0);
    }
    SecurityContextHolder.clearContext();
  }

  @Test
  void estimatePostsTheDocumentedModelWithABearerTokenAndDoesNotSave() throws Exception {
    MvcResult estimated = as(api, "user-1", post("/api/meals/estimate")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"text\":\"bowl of oatmeal\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.description").value("Oatmeal"))
        .andExpect(jsonPath("$.calories").value(150))
        .andExpect(jsonPath("$.proteinG").value(5.0))
        .andExpect(jsonPath("$.carbsG").value(27.0))
        .andExpect(jsonPath("$.fatG").value(3.0))
        .andExpect(jsonPath("$.id").doesNotExist())
        .andReturn();

    assertThat(estimated.getResponse().getContentAsString()).doesNotContain(NIM_KEY);
    assertThat(requestPath.get()).isEqualTo("/v1/chat/completions");
    assertThat(requestQuery.get()).isNull();
    assertThat(authorization.get()).matches("Bearer \\S+");
    assertThat(authorization.get()).isEqualTo("Bearer " + NIM_KEY);
    assertThat(requestBody.get()).doesNotContain(NIM_KEY);
    JsonNode sent = mapper.readTree(requestBody.get());
    assertThat(sent.path("model").asText()).isEqualTo(NvidiaDefaults.MODEL);
    assertThat(NvidiaDefaults.MODEL).isEqualTo("meta/llama-3.3-70b-instruct");
    assertThat(sent.path("stream").asBoolean()).isFalse();
    assertThat(sent.path("messages").get(1).path("content").asText()).contains("bowl of oatmeal");
    assertThat(decoyHits.get()).isZero();
    assertThat(nimHits.get()).isEqualTo(1);

    as(api, "user-1", get("/api/days/2026-09-28"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.meals").isEmpty())
        .andExpect(jsonPath("$.totals.calories").value(0))
        .andExpect(jsonPath("$.remainingCalories").value(2000));
  }

  @Test
  void missingKeyIsUnavailableAndDoesNotCallNim() throws Exception {
    as(unconfigured, "user-1", post("/api/meals/estimate")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"text\":\"two eggs\"}"))
        .andExpect(status().isServiceUnavailable())
        .andExpect(jsonPath("$.error").value("estimate_unavailable"))
        .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("NVIDIA_API_KEY")));

    assertThat(nimHits.get()).isZero();
    assertThat(decoyHits.get()).isZero();
  }

  @Test
  void nimHttp500IsBadGatewayAndDoesNotSave() throws Exception {
    stub = NimStub.HTTP_500;

    as(api, "user-1", post("/api/meals/estimate")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"text\":\"soup\"}"))
        .andExpect(status().isBadGateway())
        .andExpect(jsonPath("$.error").value("estimate_failed"))
        .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("HTTP 500")))
        .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString(NIM_KEY))));

    assertThat(nimHits.get()).isEqualTo(1);
    as(api, "user-1", get("/api/days/2026-09-28"))
        .andExpect(jsonPath("$.meals").isEmpty());
  }

  @Test
  void unusableRepliesAreBadGateway() throws Exception {
    stub = NimStub.PROSE;
    as(api, "user-1", post("/api/meals/estimate")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"text\":\"soup\"}"))
        .andExpect(status().isBadGateway())
        .andExpect(jsonPath("$.error").value("estimate_failed"))
        .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("usable form")));

    stub = NimStub.FRACTIONAL;
    as(api, "user-1", post("/api/meals/estimate")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"text\":\"soup\"}"))
        .andExpect(status().isBadGateway())
        .andExpect(jsonPath("$.error").value("estimate_failed"));

    as(api, "user-1", get("/api/days/2026-09-28"))
        .andExpect(jsonPath("$.meals").isEmpty());
  }

  @Test
  void aNimTimeoutIsBadGateway() throws Exception {
    stub = NimStub.TIMEOUT;

    as(api, "user-1", post("/api/meals/estimate")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"text\":\"soup\"}"))
        .andExpect(status().isBadGateway())
        .andExpect(jsonPath("$.error").value("estimate_failed"))
        .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("too long")));
  }

  @Test
  void estimateTextIsRequiredAndCappedAt500Characters() throws Exception {
    as(api, "user-1", post("/api/meals/estimate")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"text\":\"   \"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error").value("validation_failed"))
        .andExpect(jsonPath("$.fields.text").value("Describe the meal"));

    as(api, "user-1", post("/api/meals/estimate")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"text\":\"" + "a".repeat(501) + "\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.fields.text").value("Meal description must be 500 characters or fewer"));

    assertThat(nimHits.get()).isZero();
  }

  @Test
  void saveEditDeleteAndDayMathStayOnTheOwningUser() throws Exception {
    int hitsBeforeSave = nimHits.get();
    MvcResult created = as(api, "user-1", post("/api/meals")
            .contentType(MediaType.APPLICATION_JSON)
            .content(meal("2026-09-28", "  Oatmeal   bowl ", "1500", "40.5", "10.0", "30.0")))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.description").value("Oatmeal bowl"))
        .andExpect(jsonPath("$.calories").value(1500))
        .andExpect(jsonPath("$.day").value("2026-09-28"))
        .andReturn();
    String firstId = mapper.readTree(created.getResponse().getContentAsString()).path("id").asText();

    MvcResult second = as(api, "user-1", post("/api/meals")
            .contentType(MediaType.APPLICATION_JSON)
            .content(meal("2026-09-28", "Yogurt", "700", "10.5", "20.0", "15.5")))
        .andExpect(status().isCreated())
        .andReturn();
    String secondId = mapper.readTree(second.getResponse().getContentAsString()).path("id").asText();
    assertThat(nimHits.get()).isEqualTo(hitsBeforeSave);

    as(api, "user-1", get("/api/days/2026-09-28"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.calorieGoal").value(2000))
        .andExpect(jsonPath("$.calorieGoalSource").value("COMPUTED"))
        .andExpect(jsonPath("$.meals[0].id").value(firstId))
        .andExpect(jsonPath("$.meals[1].id").value(secondId))
        .andExpect(jsonPath("$.totals.calories").value(2200))
        .andExpect(jsonPath("$.totals.proteinG").value(51.0))
        .andExpect(jsonPath("$.totals.carbsG").value(30.0))
        .andExpect(jsonPath("$.totals.fatG").value(45.5))
        .andExpect(jsonPath("$.remainingCalories").value(-200));

    as(api, "user-1", put("/api/meals/" + secondId)
            .contentType(MediaType.APPLICATION_JSON)
            .content(meal("2026-09-29", "Yogurt cup", "200", "5.0", "10.0", "2.5")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.calories").value(200))
        .andExpect(jsonPath("$.day").value("2026-09-29"));

    as(api, "user-1", get("/api/days/2026-09-28"))
        .andExpect(jsonPath("$.meals.length()").value(1))
        .andExpect(jsonPath("$.totals.calories").value(1500))
        .andExpect(jsonPath("$.remainingCalories").value(500));
    as(api, "user-1", get("/api/days/2026-09-29"))
        .andExpect(jsonPath("$.meals[0].description").value("Yogurt cup"))
        .andExpect(jsonPath("$.totals.calories").value(200))
        .andExpect(jsonPath("$.remainingCalories").value(1800));

    as(api, "user-2", put("/api/meals/" + secondId)
            .contentType(MediaType.APPLICATION_JSON)
            .content(meal("2026-09-29", "Stolen", "1", "0", "0", "0")))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.error").value("meal_not_found"));
    as(api, "user-2", delete("/api/meals/" + secondId))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.error").value("meal_not_found"));
    as(api, "user-2", get("/api/days/2026-09-29"))
        .andExpect(jsonPath("$.meals").isEmpty())
        .andExpect(jsonPath("$.calorieGoal").value(1800))
        .andExpect(jsonPath("$.calorieGoalSource").value("OVERRIDE"))
        .andExpect(jsonPath("$.remainingCalories").value(1800));
    as(api, "user-1", get("/api/days/2026-09-29"))
        .andExpect(jsonPath("$.meals[0].id").value(secondId))
        .andExpect(jsonPath("$.meals[0].description").value("Yogurt cup"));

    as(api, "user-1", delete("/api/meals/" + firstId))
        .andExpect(status().isNoContent())
        .andExpect(content().string(""));
    as(api, "user-1", get("/api/days/2026-09-28"))
        .andExpect(jsonPath("$.meals").isEmpty())
        .andExpect(jsonPath("$.totals.calories").value(0))
        .andExpect(jsonPath("$.totals.proteinG").value(0.0))
        .andExpect(jsonPath("$.remainingCalories").value(2000));
  }

  @Test
  void fractionalCaloriesAndDaysOutsideTheWindowAreRejected() throws Exception {
    as(api, "user-1", post("/api/meals")
            .contentType(MediaType.APPLICATION_JSON)
            .content(meal("2026-09-28", "Eggs", "380.5", "18", "22", "16.5")))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error").value("invalid_body"));

    as(api, "user-1", post("/api/meals")
            .contentType(MediaType.APPLICATION_JSON)
            .content(meal("1999-12-31", "Eggs", "100", "1", "1", "1")))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error").value("validation_failed"))
        .andExpect(jsonPath("$.fields.day").exists());

    LocalDate latest = LocalDate.now().plusYears(2);
    as(api, "user-1", post("/api/meals")
            .contentType(MediaType.APPLICATION_JSON)
            .content(meal(latest.toString(), "Edges", "0", "0", "0", "0")))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.calories").value(0));
    as(api, "user-1", post("/api/meals")
            .contentType(MediaType.APPLICATION_JSON)
            .content(meal(latest.plusDays(1).toString(), "Too late", "1", "0", "0", "0")))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.fields.day").exists());

    as(api, "user-1", get("/api/days/1999-12-31"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.meals").isEmpty())
        .andExpect(jsonPath("$.calorieGoal").value(2000))
        .andExpect(jsonPath("$.remainingCalories").value(2000));
    as(api, "user-1", get("/api/days/2026-09-28"))
        .andExpect(jsonPath("$.meals").isEmpty());
  }

  @Test
  void mealRoutesRejectAMissingOrInvalidToken() throws Exception {
    api.perform(post("/api/meals/estimate")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"text\":\"oatmeal\"}"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.error").value("unauthorized"));

    api.perform(post("/api/meals/estimate")
            .header(HttpHeaders.AUTHORIZATION, "Bearer not-a-jwt")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"text\":\"oatmeal\"}"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.error").value("unauthorized"));

    api.perform(post("/api/meals")
            .contentType(MediaType.APPLICATION_JSON)
            .content(meal("2026-09-28", "Eggs", "100", "1", "1", "1")))
        .andExpect(status().isUnauthorized());

    api.perform(delete("/api/meals/meal-1"))
        .andExpect(status().isUnauthorized());

    assertThat(nimHits.get()).isZero();
  }

  private void handleNim(HttpExchange exchange) throws IOException {
    nimHits.incrementAndGet();
    requestPath.set(exchange.getRequestURI().getPath());
    requestQuery.set(exchange.getRequestURI().getRawQuery());
    authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
    requestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
    try {
      switch (stub) {
        case HTTP_500 -> write(exchange, 500, ("{\"error\":\"" + NIM_KEY + " leaked\"}").getBytes(StandardCharsets.UTF_8));
        case PROSE -> write(exchange, 200, completion("I can't help with that."));
        case FRACTIONAL -> write(exchange, 200, completion(
            "{\"description\":\"Soup\",\"calories\":12.5,\"proteinG\":1,\"carbsG\":1,\"fatG\":1}"));
        case TIMEOUT -> {
          try {
            Thread.sleep(2_000);
          } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return;
          }
          write(exchange, 200, completion(
              "{\"description\":\"Late\",\"calories\":1,\"proteinG\":0,\"carbsG\":0,\"fatG\":0}"));
        }
        case OK -> write(exchange, 200, completion(
            "{\"description\":\"Oatmeal\",\"calories\":150,\"proteinG\":5,\"carbsG\":27,\"fatG\":3}"));
      }
    } catch (IOException ex) {
      exchange.close();
    }
  }

  private byte[] completion(String content) throws IOException {
    ObjectNode root = mapper.createObjectNode();
    ObjectNode message = mapper.createObjectNode();
    message.put("content", content);
    ObjectNode choice = mapper.createObjectNode();
    choice.set("message", message);
    root.putArray("choices").add(choice);
    return mapper.writeValueAsBytes(root);
  }

  private static void write(HttpExchange exchange, int status, byte[] body) throws IOException {
    exchange.getResponseHeaders().add("Content-Type", "application/json");
    exchange.sendResponseHeaders(status, body.length);
    exchange.getResponseBody().write(body);
    exchange.close();
  }

  private MockMvc mockMvc(NimCompletionClient nim) {
    MealEstimateService estimates = new MealEstimateService(nim, new MealEstimateParser(mapper));
    MealService mealService = new MealService(meals, profiles);
    LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
    validator.afterPropertiesSet();
    return MockMvcBuilders.standaloneSetup(new MealController(estimates, mealService), new DayController(mealService))
        .setControllerAdvice(new GlobalExceptionHandler())
        .setValidator(validator)
        .setMessageConverters(new MappingJackson2HttpMessageConverter(mapper))
        .addFilters(new JwtAuthFilter(jwt))
        .build();
  }

  private ResultActions as(MockMvc mvc, String userId, MockHttpServletRequestBuilder request) throws Exception {
    return mvc.perform(request.header(HttpHeaders.AUTHORIZATION, "Bearer " + token(userId)));
  }

  private String token(String userId) {
    return tokens.computeIfAbsent(userId, id -> {
      User user = new User();
      user.setId(id);
      user.setEmail(id + "@example.com");
      return jwt.generate(user);
    });
  }

  private static String meal(
      String day, String description, String calories, String protein, String carbs, String fat) {
    return """
        {
          "day": "%s",
          "description": "%s",
          "calories": %s,
          "proteinG": %s,
          "carbsG": %s,
          "fatG": %s
        }
        """.formatted(day, description, calories, protein, carbs, fat);
  }

  private static ProfileResponse profile(
      String id, int active, Integer override, CalorieGoalSource source) {
    return new ProfileResponse(
        id,
        id + "@example.com",
        30,
        Sex.MALE,
        180,
        90,
        80,
        ActivityLevel.MODERATELY_ACTIVE,
        20,
        1880,
        2914,
        new BigDecimal("1.55"),
        550,
        2364,
        override,
        active,
        source,
        ClampReason.NONE);
  }
}
