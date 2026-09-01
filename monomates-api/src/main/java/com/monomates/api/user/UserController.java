package com.monomates.api.user;

import com.monomates.api.security.CurrentUserService;
import com.monomates.api.user.dto.*;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/users/me")
public class UserController {

  private final CurrentUserService current;

  public UserController(CurrentUserService c) {
    current = c;
  }

  @GetMapping
  public UserResponse get(@AuthenticationPrincipal Jwt j) {
    return UserResponse.from(current.require(j));
  }

  @PatchMapping
  @Transactional
  public UserResponse update(
    @AuthenticationPrincipal Jwt j,
    @Valid @RequestBody UpdateProfileRequest r
  ) {
    UserAccount u = current.require(j);
    u.setFullName(r.fullName().trim());
    return UserResponse.from(u);
  }
}
