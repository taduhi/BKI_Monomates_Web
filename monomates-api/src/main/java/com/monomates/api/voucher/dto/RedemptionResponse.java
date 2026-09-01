package com.monomates.api.voucher.dto;

import com.monomates.api.voucher.Redemption;
import java.time.Instant;
import java.util.UUID;

public record RedemptionResponse(
  UUID id,
  UUID voucherId,
  String voucherTitle,
  int tokenCost,
  String redemptionCode,
  String redemptionInstructions,
  String redemptionDisplayText,
  String status,
  Instant createdAt,
  Long remainingBalance
) {
  public static RedemptionResponse from(Redemption r, long b) {
    return new RedemptionResponse(
      r.getId(),
      r.getVoucher().getId(),
      r.getVoucher().getTitle(),
      r.getTokenCost(),
      r.getRedemptionCode(),
      r.getVoucher().getRedemptionInstructions(),
      r.getVoucher().getRedemptionDisplayText(),
      r.getStatus().name(),
      r.getCreatedAt(),
      b
    );
  }

  public static RedemptionResponse from(Redemption r) {
    return new RedemptionResponse(
      r.getId(),
      r.getVoucher().getId(),
      r.getVoucher().getTitle(),
      r.getTokenCost(),
      r.getRedemptionCode(),
      r.getVoucher().getRedemptionInstructions(),
      r.getVoucher().getRedemptionDisplayText(),
      r.getStatus().name(),
      r.getCreatedAt(),
      null
    );
  }
}
