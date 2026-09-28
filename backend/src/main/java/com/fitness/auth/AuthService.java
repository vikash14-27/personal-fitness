package com.fitness.auth;

import com.fitness.common.ApiException;
import com.fitness.profile.ProfileService;
import com.fitness.user.User;
import com.fitness.user.UserRepository;
import java.time.Instant;
import java.util.Locale;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AuthService {

  private final UserRepository users;
  private final PasswordEncoder passwordEncoder;
  private final JwtService jwtService;
  private final ProfileService profiles;

  public AuthService(
      UserRepository users,
      PasswordEncoder passwordEncoder,
      JwtService jwtService,
      ProfileService profiles) {
    this.users = users;
    this.passwordEncoder = passwordEncoder;
    this.jwtService = jwtService;
    this.profiles = profiles;
  }

  public AuthResponse signup(SignupRequest request) {
    String email = normalizeEmail(request.email());
    if (users.existsByEmail(email)) {
      throw emailTaken();
    }

    User user = new User();
    user.setEmail(email);
    user.setPasswordHash(passwordEncoder.encode(request.password()));
    user.setCreatedAt(Instant.now());
    profiles.applyBio(
        user,
        request.age(),
        request.sex(),
        request.heightCm(),
        request.currentWeightKg(),
        request.targetWeightKg(),
        request.activityLevel(),
        request.timelineWeeks(),
        null);

    try {
      user = users.save(user);
    } catch (DuplicateKeyException ex) {
      throw emailTaken();
    }
    return new AuthResponse(jwtService.generate(user), profiles.toResponse(user));
  }

  public AuthResponse login(LoginRequest request) {
    String email = normalizeEmail(request.email());
    User user = users.findByEmail(email).orElse(null);
    if (user == null || !passwordEncoder.matches(request.password(), user.getPasswordHash())) {
      throw new ApiException(HttpStatus.UNAUTHORIZED, "invalid_credentials", "Email or password is incorrect.");
    }
    return new AuthResponse(jwtService.generate(user), profiles.toResponse(user));
  }

  private static String normalizeEmail(String email) {
    return email.trim().toLowerCase(Locale.ROOT);
  }

  private static ApiException emailTaken() {
    return new ApiException(HttpStatus.CONFLICT, "email_taken", "An account with this email already exists.");
  }
}
