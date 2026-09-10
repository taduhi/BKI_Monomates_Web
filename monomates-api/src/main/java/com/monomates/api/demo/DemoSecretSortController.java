package com.monomates.api.demo;

import com.monomates.api.common.exception.NotFoundException;
import com.monomates.api.deposit.DepositProcessingService;
import com.monomates.api.deposit.DepositProcessingService.DemoOutcome;
import com.monomates.api.deposit.dto.DepositResponse;
import com.monomates.api.security.CurrentUserService;
import com.monomates.api.user.UserAccount;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

/**
 * Simulates the sorting-bin hardware classifying an item, gated by account
 * identity rather than Spring profile so it also exists on a public/prod
 * deployment. Unlike {@code LocalDemoController}, this endpoint is reachable
 * in every profile, but only the one fixed demo account configured via
 * {@code app.demo.secret-account-email} may use it; any other caller sees a
 * plain 404, matching how {@link DepositProcessingService#processDemo} already
 * hides a session that does not belong to the caller.
 */
@RestController
@RequestMapping("/api/v1/demo")
public class DemoSecretSortController {

  private final CurrentUserService current;
  private final DepositProcessingService deposits;
  private final DemoProperties props;

  public DemoSecretSortController(
    CurrentUserService c,
    DepositProcessingService d,
    DemoProperties p
  ) {
    current = c;
    deposits = d;
    props = p;
  }

  @PostMapping("/secret-sort")
  public DepositResponse sort(
    @AuthenticationPrincipal Jwt j,
    @Valid @RequestBody SecretSortRequest r
  ) {
    UserAccount user = current.require(j);
    if (
      props.secretAccountEmail() == null ||
      !props.secretAccountEmail().equalsIgnoreCase(user.getEmail())
    ) {
      throw new NotFoundException("Not found.");
    }
    return deposits.processDemo(user, r.sessionId(), r.outcome());
  }

  public record SecretSortRequest(
    @NotNull UUID sessionId,
    @NotNull DemoOutcome outcome
  ) {}
}
