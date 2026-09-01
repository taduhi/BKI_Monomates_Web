package com.monomates.api.reward.dto;

import com.monomates.api.reward.TokenLedgerEntry;
import java.time.Instant;
import java.util.UUID;

public record TokenLedgerResponse(
  UUID id,
  String type,
  int amount,
  String description,
  UUID depositId,
  UUID depositSessionId,
  UUID redemptionId,
  String binCode,
  String verificationMethod,
  String verificationStatus,
  String voucherTitle,
  String userEmail,
  String userFullName,
  Instant createdAt
) {
  public static TokenLedgerResponse from(TokenLedgerEntry e) {
    return new TokenLedgerResponse(
      e.getId(),
      e.getTransactionType().name(),
      e.getAmount(),
      e.getDescription(),
      e.getDeposit() == null ? null : e.getDeposit().getId(),
      e.getDeposit() == null ? null : e.getDeposit().getSession().getId(),
      e.getRedemption() == null ? null : e.getRedemption().getId(),
      e.getDeposit() == null
        ? null
        : e.getDeposit().getSession().getBin().getPublicCode(),
      e.getDeposit() == null ? null : "DEVICE_EVENT",
      e.getDeposit() == null
        ? null
        : e.getDeposit().getVerificationStatus().name(),
      e.getRedemption() == null
        ? null
        : e.getRedemption().getVoucher().getTitle(),
      e.getUser().getEmail(),
      e.getUser().getFullName(),
      e.getCreatedAt()
    );
  }
}
