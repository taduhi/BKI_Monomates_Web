package com.monomates.api.deposit;

import com.monomates.api.deposit.dto.SessionResponse;
import com.monomates.api.security.CurrentUserService;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
public class DepositSessionController {

  private final CurrentUserService current;
  private final DepositSessionService sessions;

  public DepositSessionController(
    CurrentUserService c,
    DepositSessionService s
  ) {
    current = c;
    sessions = s;
  }

  @PostMapping("/bins/{publicCode}/sessions")
  public SessionResponse start(
    @AuthenticationPrincipal Jwt j,
    @PathVariable String publicCode
  ) {
    return sessions.start(current.require(j), publicCode);
  }

  @GetMapping("/sessions/{sessionId}")
  public SessionResponse get(
    @AuthenticationPrincipal Jwt j,
    @PathVariable UUID sessionId
  ) {
    return sessions.getForUser(current.require(j), sessionId);
  }

  @PostMapping("/sessions/{sessionId}/cancel")
  public SessionResponse cancel(
    @AuthenticationPrincipal Jwt j,
    @PathVariable UUID sessionId
  ) {
    return sessions.cancel(current.require(j), sessionId);
  }

  @PostMapping("/sessions/{sessionId}/scan")
  public SessionResponse requestScan(
    @AuthenticationPrincipal Jwt j,
    @PathVariable UUID sessionId
  ) {
    return sessions.requestScan(current.require(j), sessionId);
  }
}
