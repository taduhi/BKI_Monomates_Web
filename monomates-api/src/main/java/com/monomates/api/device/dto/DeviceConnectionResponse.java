package com.monomates.api.device.dto;

import com.monomates.api.device.Device;
import java.time.Instant;
import java.util.UUID;

public record DeviceConnectionResponse(
  UUID binId,
  String binCode,
  String deviceCode,
  String status,
  String connectionMode,
  int bridgePort,
  String acceptedDirection,
  boolean swapDirections,
  String activeTransport,
  Instant lastHeartbeatAt
) {
  public static DeviceConnectionResponse from(Device device) {
    return new DeviceConnectionResponse(
      device.getBin().getId(),
      device.getBin().getPublicCode(),
      device.getDeviceCode(),
      device.getStatus().name(),
      device.getConnectionMode().name(),
      device.getBridgePort(),
      device.getAcceptedDirection().name(),
      device.isSwapDirections(),
      device.getActiveTransport() == null
        ? null
        : device.getActiveTransport().name(),
      device.getLastHeartbeatAt()
    );
  }
}
