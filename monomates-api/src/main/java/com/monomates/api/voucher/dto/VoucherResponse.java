package com.monomates.api.voucher.dto;

import com.monomates.api.voucher.Voucher;
import java.time.Instant;
import java.util.UUID;

public record VoucherResponse(
  UUID id,
  String partnerName,
  String title,
  String description,
  int tokenCost,
  int inventory,
  Instant validFrom,
  Instant validUntil,
  String status,
  String imageUrl,
  String termsAndConditions,
  String redemptionInstructions,
  String redemptionDisplayText,
  int maxRedemptionsPerUser
) {
  public static VoucherResponse from(Voucher v) {
    return new VoucherResponse(
      v.getId(),
      v.getPartnerName(),
      v.getTitle(),
      v.getDescription(),
      v.getTokenCost(),
      v.getInventory(),
      v.getValidFrom(),
      v.getValidUntil(),
      v.getStatus().name(),
      v.getImageUrl(),
      v.getTermsAndConditions(),
      v.getRedemptionInstructions(),
      v.getRedemptionDisplayText(),
      v.getMaxRedemptionsPerUser()
    );
  }
}
