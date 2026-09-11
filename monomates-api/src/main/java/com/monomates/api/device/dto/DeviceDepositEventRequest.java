package com.monomates.api.device.dto;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record DeviceDepositEventRequest(
  @NotBlank String deviceCode,
  @NotBlank String deviceSecret,
  @NotBlank @Size(max = 160) String eventId,
  // Optional: a browser-driven caller (the demo/testing endpoints) already
  // knows which session it started and passes it directly. Real bin
  // hardware never learns a session id — only a user's phone does — so it
  // may omit this entirely; the backend then finds whichever session is
  // currently ACTIVE for the device's own bin (see DepositProcessingService).
  UUID sessionId,
  boolean irDetected,
  @DecimalMin("0.0") BigDecimal weightChangeGrams,
  String itemType,
  @DecimalMin("0.0") @DecimalMax("1.0") BigDecimal classificationConfidence,
  Instant recordedAt,
  String rawPayload
) {}
