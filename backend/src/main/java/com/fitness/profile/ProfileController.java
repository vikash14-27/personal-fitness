package com.fitness.profile;

import com.fitness.common.CurrentUser;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/profile")
public class ProfileController {

  private final ProfileService profiles;

  public ProfileController(ProfileService profiles) {
    this.profiles = profiles;
  }

  @GetMapping
  public ProfileResponse current() {
    return profiles.get(CurrentUser.id());
  }

  @PutMapping
  public ProfileResponse update(@Valid @RequestBody UpdateProfileRequest request) {
    return profiles.update(CurrentUser.id(), request);
  }
}
