package com.monomates.api.device;

import com.monomates.api.bin.RecyclingBin;
import com.monomates.api.common.model.BaseEntity;
import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "devices")
public class Device extends BaseEntity {

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "bin_id", nullable = false)
  private RecyclingBin bin;

  @Column(name = "device_code", nullable = false, unique = true)
  private String deviceCode;

  @Column(name = "secret_hash", nullable = false)
  private String secretHash;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private DeviceStatus status;

  @Column(name = "firmware_version")
  private String firmwareVersion;

  @Column(name = "last_heartbeat_at")
  private Instant lastHeartbeatAt;

  @Enumerated(EnumType.STRING)
  @Column(name = "connection_mode", nullable = false)
  private DeviceConnectionMode connectionMode = DeviceConnectionMode.AUTO;

  @Column(name = "bridge_port", nullable = false)
  private int bridgePort = 8000;

  @Enumerated(EnumType.STRING)
  @Column(name = "accepted_direction", nullable = false)
  private SortDirection acceptedDirection = SortDirection.RIGHT;

  @Column(name = "swap_directions", nullable = false)
  private boolean swapDirections;

  @Enumerated(EnumType.STRING)
  @Column(name = "active_transport")
  private DeviceTransport activeTransport;

  protected Device() {}

  public Device(RecyclingBin b, String c, String h, DeviceStatus s, String f) {
    bin = b;
    deviceCode = c;
    secretHash = h;
    status = s;
    firmwareVersion = f;
  }

  public RecyclingBin getBin() {
    return bin;
  }

  public String getDeviceCode() {
    return deviceCode;
  }

  public String getSecretHash() {
    return secretHash;
  }

  public DeviceStatus getStatus() {
    return status;
  }

  public String getFirmwareVersion() {
    return firmwareVersion;
  }

  public Instant getLastHeartbeatAt() {
    return lastHeartbeatAt;
  }

  public DeviceConnectionMode getConnectionMode() {
    return connectionMode;
  }

  public int getBridgePort() {
    return bridgePort;
  }

  public SortDirection getAcceptedDirection() {
    return acceptedDirection;
  }

  public boolean isSwapDirections() {
    return swapDirections;
  }

  public DeviceTransport getActiveTransport() {
    return activeTransport;
  }

  public void updateConfiguration(DeviceStatus status, String firmwareVersion) {
    this.status = status;
    this.firmwareVersion = firmwareVersion;
  }

  public void updateConnection(
    DeviceConnectionMode mode,
    int port,
    SortDirection direction,
    boolean swapDirections
  ) {
    connectionMode = mode;
    bridgePort = port;
    acceptedDirection = direction;
    this.swapDirections = swapDirections;
  }

  public void heartbeat(String f, DeviceTransport transport, Instant t) {
    firmwareVersion = f;
    activeTransport = transport;
    lastHeartbeatAt = t;
    status = DeviceStatus.ACTIVE;
    bin.markSeen(t);
  }
}
