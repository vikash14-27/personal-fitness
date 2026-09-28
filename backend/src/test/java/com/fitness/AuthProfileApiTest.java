package com.fitness;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fitness.auth.AuthService;
import com.fitness.auth.JwtAuthFilter;
import com.fitness.auth.JwtService;
import com.fitness.calorie.CalorieCalculator;
import com.fitness.config.AppProperties;
import com.fitness.config.RestAuthEntryPoint;
import com.fitness.config.SecurityConfig;
import com.fitness.profile.ProfileService;
import com.fitness.user.User;
import com.fitness.user.UserRepository;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import javax.crypto.SecretKey;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@WebMvcTest(controllers = {
    com.fitness.auth.AuthController.class,
    com.fitness.profile.ProfileController.class,
    com.fitness.health.HealthController.class
})
@AutoConfigureMockMvc
@Import({
    SecurityConfig.class,
    RestAuthEntryPoint.class,
    JwtAuthFilter.class,
    AuthService.class,
    ProfileService.class,
    CalorieCalculator.class,
    JwtService.class
})
@EnableConfigurationProperties(AppProperties.class)
@TestPropertySource(properties = {
    "app.jwt.secret=dev-only-jwt-secret-change-me-use-32b-min!!",
    "app.jwt.expiration-ms=3600000",
    "app.cors-origins=http://localhost:5173"
})
class AuthProfileApiTest {

  private static final String SECRET = "dev-only-jwt-secret-change-me-use-32b-min!!";

  @Autowired
  private MockMvc mockMvc;

  @Autowired
  private ObjectMapper objectMapper;

  @MockitoBean
  private UserRepository users;

  private final Map<String, User> byId = new LinkedHashMap<>();

  @BeforeEach
  void stubUsers() {
    byId.clear();
    lenient().when(users.existsByEmail(anyString())).thenAnswer(invocation ->
        byId.values().stream().anyMatch(user -> user.getEmail().equals(invocation.getArgument(0))));
    lenient().when(users.findByEmail(anyString())).thenAnswer(invocation ->
        byId.values().stream()
            .filter(user -> user.getEmail().equals(invocation.getArgument(0)))
            .findFirst());
    lenient().when(users.findById(anyString())).thenAnswer(invocation ->
        Optional.ofNullable(byId.get(invocation.getArgument(0))));
    lenient().when(users.save(any(User.class))).thenAnswer(invocation -> {
      User user = invocation.getArgument(0);
      if (user.getId() == null || user.getId().isBlank()) {
        user.setId("user-" + (byId.size() + 1));
      }
      byId.put(user.getId(), user);
      return user;
    });
  }

  @Test
  void healthIsPublic() throws Exception {
    mockMvc.perform(get("/api/health"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("UP"));
  }

  @Test
  void signupReturnsAJwtAndTheReadmeCalorieGoal() throws Exception {
    MvcResult result = mockMvc.perform(post("/api/auth/signup")
            .contentType(MediaType.APPLICATION_JSON)
            .content(signupBody("Ada@Example.com", "password123")))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.token").isNotEmpty())
        .andExpect(jsonPath("$.profile.email").value("ada@example.com"))
        .andExpect(jsonPath("$.profile.age").value(30))
        .andExpect(jsonPath("$.profile.sex").value("MALE"))
        .andExpect(jsonPath("$.profile.heightCm").value(180))
        .andExpect(jsonPath("$.profile.currentWeightKg").value(90))
        .andExpect(jsonPath("$.profile.targetWeightKg").value(80))
        .andExpect(jsonPath("$.profile.activityLevel").value("MODERATELY_ACTIVE"))
        .andExpect(jsonPath("$.profile.timelineWeeks").value(20))
        .andExpect(jsonPath("$.profile.bmr").value(1880))
        .andExpect(jsonPath("$.profile.tdee").value(2914))
        .andExpect(jsonPath("$.profile.dailyAdjustment").value(550))
        .andExpect(jsonPath("$.profile.computedCalorieGoal").value(2364))
        .andExpect(jsonPath("$.profile.activeCalorieGoal").value(2364))
        .andExpect(jsonPath("$.profile.calorieGoalSource").value("COMPUTED"))
        .andExpect(jsonPath("$.profile.calorieGoalClamp").value("NONE"))
        .andReturn();

    String token = token(result);
    mockMvc.perform(get("/api/profile").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.activeCalorieGoal").value(2364))
        .andExpect(jsonPath("$.calorieGoalSource").value("COMPUTED"));
  }

  @Test
  void signupRejectsADuplicateEmailIgnoringCase() throws Exception {
    signup("Ada@Example.com");

    mockMvc.perform(post("/api/auth/signup")
            .contentType(MediaType.APPLICATION_JSON)
            .content(signupBody("ada@example.com", "password123")))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.error").value("email_taken"))
        .andExpect(jsonPath("$.message").value("An account with this email already exists."));
  }

  @Test
  void signupValidationReturnsAFieldsMap() throws Exception {
    mockMvc.perform(post("/api/auth/signup")
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {
                  "email": "not-an-email",
                  "password": "short",
                  "age": 10,
                  "sex": "MALE",
                  "heightCm": 90,
                  "currentWeightKg": 10,
                  "targetWeightKg": 400,
                  "activityLevel": "SEDENTARY",
                  "timelineWeeks": 0
                }
                """))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error").value("validation_failed"))
        .andExpect(jsonPath("$.message").value("Check the highlighted fields."))
        .andExpect(jsonPath("$.fields.email").value("Enter a valid email address"))
        .andExpect(jsonPath("$.fields.password").value("Password must be 8–72 characters"))
        .andExpect(jsonPath("$.fields.age").value("Age must be between 16 and 100"))
        .andExpect(jsonPath("$.fields.heightCm").value("Height must be between 120 and 250 cm"))
        .andExpect(jsonPath("$.fields.currentWeightKg").value("Current weight must be between 30 and 300 kg"))
        .andExpect(jsonPath("$.fields.targetWeightKg").value("Target weight must be between 30 and 300 kg"))
        .andExpect(jsonPath("$.fields.timelineWeeks").value("Timeline must be between 1 and 104 weeks"));
  }

  @Test
  void loginReturnsAJwtAndRejectsWrongCredentials() throws Exception {
    signup("ada@example.com");

    MvcResult result = mockMvc.perform(post("/api/auth/login")
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {"email":"Ada@Example.com","password":"password123"}
                """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.token").isNotEmpty())
        .andExpect(jsonPath("$.profile.email").value("ada@example.com"))
        .andExpect(jsonPath("$.profile.activeCalorieGoal").value(2364))
        .andReturn();

    mockMvc.perform(get("/api/profile").header(HttpHeaders.AUTHORIZATION, "Bearer " + token(result)))
        .andExpect(status().isOk());

    mockMvc.perform(post("/api/auth/login")
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {"email":"ada@example.com","password":"wrong-password"}
                """))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.error").value("invalid_credentials"))
        .andExpect(jsonPath("$.message").value("Email or password is incorrect."));

    mockMvc.perform(post("/api/auth/login")
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {"email":"missing@example.com","password":"password123"}
                """))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.error").value("invalid_credentials"))
        .andExpect(jsonPath("$.message").value("Email or password is incorrect."));
  }

  @Test
  void logoutRequiresABearerTokenAndDoesNotRevokeIt() throws Exception {
    mockMvc.perform(post("/api/auth/logout"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.error").value("unauthorized"))
        .andExpect(jsonPath("$.message").value("Sign in required."));

    String token = signup("ada@example.com");
    mockMvc.perform(post("/api/auth/logout").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
        .andExpect(status().isNoContent());

    mockMvc.perform(get("/api/profile").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.activeCalorieGoal").value(2364));
  }

  @Test
  void protectedRoutesRejectMissingAndInvalidBearerTokens() throws Exception {
    String token = signup("ada@example.com");

    mockMvc.perform(get("/api/profile"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.error").value("unauthorized"));
    mockMvc.perform(put("/api/profile")
            .contentType(MediaType.APPLICATION_JSON)
            .content(profileBody(80, "null")))
        .andExpect(status().isUnauthorized());
    mockMvc.perform(get("/api/profile").header(HttpHeaders.AUTHORIZATION, token))
        .andExpect(status().isUnauthorized());
    mockMvc.perform(get("/api/profile").header(HttpHeaders.AUTHORIZATION, "Bearer "))
        .andExpect(status().isUnauthorized());
    mockMvc.perform(get("/api/profile").header(HttpHeaders.AUTHORIZATION, "Bearer " + token + "x"))
        .andExpect(status().isUnauthorized());
    mockMvc.perform(get("/api/profile").header(HttpHeaders.AUTHORIZATION, "Bearer " + expiredToken()))
        .andExpect(status().isUnauthorized());

    byId.clear();
    mockMvc.perform(get("/api/profile").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.error").value("unauthorized"))
        .andExpect(jsonPath("$.message").value("Sign in required."));
  }

  @Test
  void savingBioRecomputesTheGoal() throws Exception {
    String token = signup("ada@example.com");

    mockMvc.perform(put("/api/profile")
            .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
            .contentType(MediaType.APPLICATION_JSON)
            .content(profileBody(90, "null")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.targetWeightKg").value(90))
        .andExpect(jsonPath("$.dailyAdjustment").value(0))
        .andExpect(jsonPath("$.computedCalorieGoal").value(2914))
        .andExpect(jsonPath("$.activeCalorieGoal").value(2914))
        .andExpect(jsonPath("$.calorieGoalSource").value("COMPUTED"))
        .andExpect(jsonPath("$.calorieGoalClamp").value("NONE"))
        .andExpect(jsonPath("$.calorieGoalOverride").value(org.hamcrest.Matchers.nullValue()));
  }

  @Test
  void overrideSetsTheActiveGoalAndNullClearsIt() throws Exception {
    String token = signup("ada@example.com");

    mockMvc.perform(put("/api/profile")
            .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
            .contentType(MediaType.APPLICATION_JSON)
            .content(profileBody(80, "2200")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.computedCalorieGoal").value(2364))
        .andExpect(jsonPath("$.calorieGoalOverride").value(2200))
        .andExpect(jsonPath("$.activeCalorieGoal").value(2200))
        .andExpect(jsonPath("$.calorieGoalSource").value("OVERRIDE"));

    mockMvc.perform(put("/api/profile")
            .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
            .contentType(MediaType.APPLICATION_JSON)
            .content(profileBody(90, "2200")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.computedCalorieGoal").value(2914))
        .andExpect(jsonPath("$.activeCalorieGoal").value(2200))
        .andExpect(jsonPath("$.calorieGoalSource").value("OVERRIDE"));

    mockMvc.perform(put("/api/profile")
            .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
            .contentType(MediaType.APPLICATION_JSON)
            .content(profileBody(90, "null")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.computedCalorieGoal").value(2914))
        .andExpect(jsonPath("$.activeCalorieGoal").value(2914))
        .andExpect(jsonPath("$.calorieGoalSource").value("COMPUTED"));

    mockMvc.perform(put("/api/profile")
            .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
            .contentType(MediaType.APPLICATION_JSON)
            .content(profileBody(80, "1000")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.calorieGoalOverride").value(1000))
        .andExpect(jsonPath("$.activeCalorieGoal").value(1000))
        .andExpect(jsonPath("$.computedCalorieGoal").value(2364))
        .andExpect(jsonPath("$.calorieGoalSource").value("OVERRIDE"));

    mockMvc.perform(put("/api/profile")
            .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
            .contentType(MediaType.APPLICATION_JSON)
            .content(profileBody(80, "5000")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.calorieGoalOverride").value(5000))
        .andExpect(jsonPath("$.activeCalorieGoal").value(5000))
        .andExpect(jsonPath("$.computedCalorieGoal").value(2364));
  }

  @Test
  void fractionalManualCalorieGoalIsRejected() throws Exception {
    String token = signup("ada@example.com");

    MvcResult result = mockMvc.perform(put("/api/profile")
            .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
            .contentType(MediaType.APPLICATION_JSON)
            .content(profileBody(80, "2200.5")))
        .andReturn();

    assertThat(result.getResponse().getStatus())
        .as("a manual calorie goal must be a whole number; body=%s", result.getResponse().getContentAsString())
        .isEqualTo(400);
  }

  @Test
  void nonNumericOverrideIsRejected() throws Exception {
    String token = signup("ada@example.com");

    mockMvc.perform(put("/api/profile")
            .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
            .contentType(MediaType.APPLICATION_JSON)
            .content(profileBody(80, "\"lots\"")))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error").value("invalid_body"));
  }

  @Test
  void overrideOutsideTheAllowedRangeIsRejected() throws Exception {
    String token = signup("ada@example.com");

    mockMvc.perform(put("/api/profile")
            .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
            .contentType(MediaType.APPLICATION_JSON)
            .content(profileBody(80, "999")))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error").value("validation_failed"))
        .andExpect(jsonPath("$.fields.calorieGoalOverride").value("Manual calorie goal must be between 1000 and 5000 kcal"));

    mockMvc.perform(put("/api/profile")
            .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
            .contentType(MediaType.APPLICATION_JSON)
            .content(profileBody(80, "5001")))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.fields.calorieGoalOverride").value("Manual calorie goal must be between 1000 and 5000 kcal"));

    mockMvc.perform(get("/api/profile").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.activeCalorieGoal").value(2364))
        .andExpect(jsonPath("$.calorieGoalSource").value("COMPUTED"));
  }

  private String signup(String email) throws Exception {
    MvcResult result = mockMvc.perform(post("/api/auth/signup")
            .contentType(MediaType.APPLICATION_JSON)
            .content(signupBody(email, "password123")))
        .andExpect(status().isCreated())
        .andReturn();
    return token(result);
  }

  private String token(MvcResult result) throws Exception {
    JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
    return body.get("token").asText();
  }

  private static String signupBody(String email, String password) {
    return """
        {
          "email": "%s",
          "password": "%s",
          "age": 30,
          "sex": "MALE",
          "heightCm": 180,
          "currentWeightKg": 90,
          "targetWeightKg": 80,
          "activityLevel": "MODERATELY_ACTIVE",
          "timelineWeeks": 20
        }
        """.formatted(email, password);
  }

  private static String profileBody(int targetWeightKg, String overrideJson) {
    return """
        {
          "age": 30,
          "sex": "MALE",
          "heightCm": 180,
          "currentWeightKg": 90,
          "targetWeightKg": %d,
          "activityLevel": "MODERATELY_ACTIVE",
          "timelineWeeks": 20,
          "calorieGoalOverride": %s
        }
        """.formatted(targetWeightKg, overrideJson);
  }

  private static String expiredToken() {
    SecretKey key = Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));
    Instant past = Instant.now().minusSeconds(120);
    return Jwts.builder()
        .issuer("fitness-api")
        .subject("user-1")
        .issuedAt(Date.from(past.minusSeconds(60)))
        .expiration(Date.from(past))
        .signWith(key)
        .compact();
  }
}
