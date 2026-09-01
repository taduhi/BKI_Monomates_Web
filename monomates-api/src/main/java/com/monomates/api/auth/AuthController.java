package com.monomates.api.auth;

import com.monomates.api.auth.dto.*;
import com.monomates.api.security.*;
import com.monomates.api.user.dto.UserResponse;
import jakarta.validation.Valid;
import java.time.Duration;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

  private final AuthService auth;
  private final AuthProperties p;
  private final CurrentUserService current;

  public AuthController(AuthService a, AuthProperties p, CurrentUserService c) {
    auth = a;
    this.p = p;
    current = c;
  }

  @PostMapping("/register")
  public ResponseEntity<AuthResponse> register(
    @Valid @RequestBody RegisterRequest r
  ) {
    return withCookie(auth.register(r));
  }

  @GetMapping("/csrf")
  public CsrfToken csrf(CsrfToken token) {
    return token;
  }

  @PostMapping("/login")
  public ResponseEntity<AuthResponse> login(
    @Valid @RequestBody LoginRequest r
  ) {
    return withCookie(auth.login(r));
  }

  @PostMapping("/logout")
  public ResponseEntity<Void> logout() {
    ResponseCookie c = ResponseCookie.from(p.cookieName(), "")
      .httpOnly(true)
      .secure(p.cookieSecure())
      .sameSite(p.cookieSameSite())
      .path("/")
      .maxAge(Duration.ZERO)
      .build();
    return ResponseEntity.noContent()
      .header(HttpHeaders.SET_COOKIE, c.toString())
      .build();
  }

  @GetMapping("/me")
  public UserResponse me(@AuthenticationPrincipal Jwt j) {
    return UserResponse.from(current.require(j));
  }

  private ResponseEntity<AuthResponse> withCookie(AuthService.LoginResult r) {
    ResponseCookie c = ResponseCookie.from(p.cookieName(), r.token())
      .httpOnly(true)
      .secure(p.cookieSecure())
      .sameSite(p.cookieSameSite())
      .path("/")
      .maxAge(Duration.ofMinutes(p.accessTokenMinutes()))
      .build();
    return ResponseEntity.ok()
      .header(HttpHeaders.SET_COOKIE, c.toString())
      .body(r.response());
  }
}
