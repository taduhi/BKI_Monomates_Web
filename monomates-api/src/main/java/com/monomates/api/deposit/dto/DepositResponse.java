package com.monomates.api.deposit.dto;

import com.monomates.api.deposit.Deposit;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record DepositResponse(
  UUID id,
  UUID sessionId,
  String binCode,
  String binName,
  String status,
  String itemType,
  BigDecimal weightGrams,
  BigDecimal classificationConfidence,
  String rejectionReason,
  Instant verifiedAt,
  int tokensAwarded
) {
  public static DepositResponse from(Deposit d, int t) {
    return new DepositResponse(
      d.getId(),
      d.getSession().getId(),
      d.getSession().getBin().getPublicCode(),
      d.getSession().getBin().getName(),
      d.getVerificationStatus().name(),
      d.getItemType() == null ? null : d.getItemType().getCode(),
      d.getMeasuredWeightGrams(),
      d.getClassificationConfidence(),
      d.getRejectionReason(),
      d.getVerifiedAt(),
      t
    );
  }
}
