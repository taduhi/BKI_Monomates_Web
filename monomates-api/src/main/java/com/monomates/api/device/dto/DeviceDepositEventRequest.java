package com.monomates.api.device.dto;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record DeviceDepositEventRequest(
  @NotBlank String deviceCode,
  @NotBlank String deviceSecret,
  @NotBlank @Size(max = 160) String eventId,
  @NotNull UUID sessionId,
  boolean irDetected,
  @DecimalMin("0.0") BigDecimal weightChangeGrams,
  String itemType,
  @DecimalMin("0.0") @DecimalMax("1.0") BigDecimal classificationConfidence,
  Instant recordedAt,
  String rawPayload
) {}
