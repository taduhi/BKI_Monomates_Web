package com.monomates.api.bin.dto;

import com.monomates.api.bin.BinStatus;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.util.Set;

public record SaveBinRequest(
  @NotBlank @Size(max = 100) String publicCode,
  @NotBlank @Size(max = 180) String name,
  @NotNull BinStatus status,
  @Min(0) @Max(100) int capacityPercent,
  @NotBlank @Size(max = 180) String locationName,
  @NotBlank @Size(max = 500) String address,
  BigDecimal latitude,
  BigDecimal longitude,
  @NotEmpty Set<String> acceptedItemCodes
) {}
