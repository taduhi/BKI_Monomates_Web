package com.monomates.api.deposit.dto;

import com.monomates.api.deposit.DepositSession;
import java.time.Instant;
import java.util.UUID;

public record SessionResponse(
  UUID sessionId,
  String status,
  String binCode,
  String binName,
  Instant startedAt,
  Instant expiresAt,
  Instant completedAt,
  DepositResponse deposit,
  int tokensAwarded
) {
  public static SessionResponse from(
    DepositSession s,
    DepositResponse d,
    int t
  ) {
    return new SessionResponse(
      s.getId(),
      s.getStatus().name(),
      s.getBin().getPublicCode(),
      s.getBin().getName(),
      s.getStartedAt(),
      s.getExpiresAt(),
      s.getCompletedAt(),
      d,
      t
    );
  }
}
