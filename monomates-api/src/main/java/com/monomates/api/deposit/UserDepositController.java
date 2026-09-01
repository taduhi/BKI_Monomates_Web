package com.monomates.api.deposit;

import com.monomates.api.deposit.dto.DepositResponse;
import com.monomates.api.reward.RewardService;
import com.monomates.api.security.CurrentUserService;
import com.monomates.api.user.UserAccount;
import java.util.List;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/users/me/deposits")
public class UserDepositController {

  private final CurrentUserService current;
  private final DepositRepository deposits;
  private final RewardService rewards;

  public UserDepositController(
    CurrentUserService c,
    DepositRepository d,
    RewardService r
  ) {
    current = c;
    deposits = d;
    rewards = r;
  }

  @GetMapping
  public List<DepositResponse> list(@AuthenticationPrincipal Jwt j) {
    UserAccount u = current.require(j);
    return deposits
      .findBySession_User_IdOrderByCreatedAtDesc(u.getId())
      .stream()
      .map(d -> DepositResponse.from(d, rewards.tokensForDeposit(d)))
      .toList();
  }
}
