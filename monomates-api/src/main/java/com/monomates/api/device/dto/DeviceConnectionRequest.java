package com.monomates.api.device.dto;

import com.monomates.api.device.DeviceConnectionMode;
import com.monomates.api.device.SortDirection;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record DeviceConnectionRequest(
  @NotNull DeviceConnectionMode connectionMode,
  @Min(1) @Max(65535) int bridgePort,
  @NotNull SortDirection acceptedDirection,
  boolean swapDirections
) {}
