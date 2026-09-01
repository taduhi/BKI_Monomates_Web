package com.monomates.api.admin;

import com.monomates.api.bin.RecyclingBinRepository;
import com.monomates.api.deposit.DepositRepository;
import com.monomates.api.reward.TokenLedgerRepository;
import com.monomates.api.user.UserRepository;
import com.monomates.api.voucher.RedemptionRepository;
import java.util.Map;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin/analytics")
public class AdminAnalyticsController {

  private final UserRepository users;
  private final RecyclingBinRepository bins;
  private final DepositRepository deposits;
  private final TokenLedgerRepository ledger;
  private final RedemptionRepository redemptions;

  public AdminAnalyticsController(
    UserRepository u,
    RecyclingBinRepository b,
    DepositRepository d,
    TokenLedgerRepository l,
    RedemptionRepository r
  ) {
    users = u;
    bins = b;
    deposits = d;
    ledger = l;
    redemptions = r;
  }

  @GetMapping("/summary")
  public Map<String, Long> summary() {
    return Map.of(
      "users",
      users.count(),
      "bins",
      bins.count(),
      "deposits",
      deposits.count(),
      "ledgerEntries",
      ledger.count(),
      "redemptions",
      redemptions.count()
    );
  }
}
