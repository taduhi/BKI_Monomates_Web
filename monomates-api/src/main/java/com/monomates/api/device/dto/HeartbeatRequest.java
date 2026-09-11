package com.monomates.api.device.dto;

import jakarta.validation.constraints.NotBlank;
import com.monomates.api.device.DeviceTransport;

public record HeartbeatRequest(
  @NotBlank String deviceCode,
  @NotBlank String deviceSecret,
  String firmwareVersion,
  DeviceTransport transport
) {}
