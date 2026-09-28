package com.fitness.common;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

public final class CurrentUser {

  private CurrentUser() {}

  public static String id() {
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    if (authentication == null
        || !authentication.isAuthenticated()
        || authentication.getPrincipal() == null
        || "anonymousUser".equals(authentication.getPrincipal())) {
      throw new ApiException(HttpStatus.UNAUTHORIZED, "unauthorized", "Sign in required.");
    }
    return authentication.getPrincipal().toString();
  }
}
