package com.monomates.api.reward;

import com.monomates.api.reward.dto.*;
import com.monomates.api.security.CurrentUserService;
import com.monomates.api.user.UserAccount;
import java.util.List;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/users/me")
public class RewardController {

  private final CurrentUserService current;
  private final RewardService rewards;
  private final TokenLedgerRepository ledger;

  public RewardController(
    CurrentUserService c,
    RewardService r,
    TokenLedgerRepository l
  ) {
    current = c;
    rewards = r;
    ledger = l;
  }

  @GetMapping("/token-balance")
  public TokenBalanceResponse balance(@AuthenticationPrincipal Jwt j) {
    return new TokenBalanceResponse(rewards.balance(current.require(j)), "PT");
  }

  @GetMapping("/token-ledger")
  public List<TokenLedgerResponse> entries(@AuthenticationPrincipal Jwt j) {
    UserAccount u = current.require(j);
    return ledger
      .findByUser_IdOrderByCreatedAtDesc(u.getId())
      .stream()
      .map(TokenLedgerResponse::from)
      .toList();
  }
}
