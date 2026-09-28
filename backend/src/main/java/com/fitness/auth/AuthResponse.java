package com.fitness.auth;

import com.fitness.profile.ProfileResponse;

public record AuthResponse(String token, ProfileResponse profile) {}
