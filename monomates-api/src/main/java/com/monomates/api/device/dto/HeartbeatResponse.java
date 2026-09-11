package com.monomates.api.device.dto;

import com.monomates.api.deposit.DepositSession;
import com.monomates.api.device.Device;
import java.time.Instant;
import java.util.UUID;

public record HeartbeatResponse(
  String status,
  Instant serverTime,
  String binCode,
  String connectionMode,
  int bridgePort,
  String acceptedDirection,
  boolean swapDirections,
  String activeTransport,
  String command,
  UUID sessionId,
  Instant scanRequestedAt
) {
  public static HeartbeatResponse from(
    Device device,
    DepositSession pendingSession,
    Instant serverTime
  ) {
    boolean hasScan = pendingSession != null;
    return new HeartbeatResponse(
      "accepted",
      serverTime,
      device.getBin().getPublicCode(),
      device.getConnectionMode().name(),
      device.getBridgePort(),
      device.getAcceptedDirection().name(),
      device.isSwapDirections(),
      device.getActiveTransport() == null
        ? null
        : device.getActiveTransport().name(),
      hasScan ? "SCAN_ITEM" : "IDLE",
      hasScan ? pendingSession.getId() : null,
      hasScan ? pendingSession.getScanRequestedAt() : null
    );
  }
}
