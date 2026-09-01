package com.monomates.api.voucher.dto;

import com.monomates.api.voucher.VoucherStatus;
import jakarta.validation.constraints.*;
import java.time.Instant;

public record SaveVoucherRequest(
  @NotBlank @Size(max = 180) String partnerName,
  @NotBlank @Size(max = 220) String title,
  @Size(max = 800) String description,
  @Min(1) int tokenCost,
  @Min(0) int inventory,
  Instant validFrom,
  Instant validUntil,
  @NotNull VoucherStatus status,
  @Size(max = 1000) String imageUrl,
  @Size(max = 2000) String termsAndConditions,
  @Size(max = 1000) String redemptionInstructions,
  @Size(max = 300) String redemptionDisplayText,
  @Min(1) Integer maxRedemptionsPerUser
) {}
