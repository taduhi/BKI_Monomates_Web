package com.monomates.api.device.dto;

import jakarta.validation.constraints.NotBlank;

public record HeartbeatRequest(
  @NotBlank String deviceCode,
  @NotBlank String deviceSecret,
  String firmwareVersion
) {}
