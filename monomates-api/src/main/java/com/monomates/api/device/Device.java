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

  public void updateConfiguration(DeviceStatus status, String firmwareVersion) {
    this.status = status;
    this.firmwareVersion = firmwareVersion;
  }

  public void heartbeat(String f, Instant t) {
    firmwareVersion = f;
    lastHeartbeatAt = t;
    status = DeviceStatus.ACTIVE;
    bin.markSeen(t);
  }
}
