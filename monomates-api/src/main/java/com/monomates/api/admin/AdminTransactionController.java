package com.monomates.api.admin;

import com.monomates.api.reward.*;
import com.monomates.api.reward.dto.TokenLedgerResponse;
import java.util.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin/transactions")
public class AdminTransactionController {

  private final TokenLedgerRepository ledger;

  public AdminTransactionController(TokenLedgerRepository l) {
    ledger = l;
  }

  @GetMapping
  public List<TokenLedgerResponse> list() {
    return ledger
      .findAll()
      .stream()
      .sorted(Comparator.comparing(TokenLedgerEntry::getCreatedAt).reversed())
      .map(TokenLedgerResponse::from)
      .toList();
  }
}
