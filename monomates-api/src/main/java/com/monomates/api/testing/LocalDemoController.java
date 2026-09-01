package com.monomates.api.testing;

import com.monomates.api.deposit.DepositProcessingService;
import com.monomates.api.deposit.dto.DepositResponse;
import com.monomates.api.security.CurrentUserService;
import com.monomates.api.user.UserAccount;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@Profile("local")
@RequestMapping("/api/v1/testing")
public class LocalDemoController {

  private final CurrentUserService current;
  private final DepositProcessingService deposits;

  public LocalDemoController(CurrentUserService c, DepositProcessingService d) {
    current = c;
    deposits = d;
  }

  @PostMapping("/simulate-deposit")
  public DepositResponse simulate(
    @AuthenticationPrincipal Jwt j,
    @Valid @RequestBody SimulateRequest r
  ) {
    UserAccount user = current.require(j);
    return deposits.processDemo(user, r.sessionId(), r.outcome());
  }

  public record SimulateRequest(
    @NotNull UUID sessionId,
    @NotNull DepositProcessingService.DemoOutcome outcome
  ) {}
}
