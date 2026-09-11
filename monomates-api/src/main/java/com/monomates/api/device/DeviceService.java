package com.monomates.api.device;

import com.monomates.api.common.exception.*;
import com.monomates.api.deposit.DepositSession;
import com.monomates.api.deposit.DepositSessionRepository;
import com.monomates.api.deposit.SessionStatus;
import com.monomates.api.device.dto.*;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DeviceService {

  private final DeviceRepository devices;
  private final PasswordEncoder passwords;
  private final DepositSessionRepository sessions;

  public DeviceService(
    DeviceRepository d,
    PasswordEncoder p,
    DepositSessionRepository sessions
  ) {
    devices = d;
    passwords = p;
    this.sessions = sessions;
  }

  @Transactional(readOnly = true)
  public Device authenticate(String code, String secret) {
    Device d = devices
      .findByDeviceCodeIgnoreCase(code)
      .orElseThrow(() -> new NotFoundException("Device was not found."));
    if (
      d.getStatus() == DeviceStatus.DISABLED ||
      !passwords.matches(secret, d.getSecretHash())
    ) throw new BusinessRuleException("Device authentication failed.");
    return d;
  }

  @Transactional
  public HeartbeatResponse heartbeat(HeartbeatRequest r) {
    Device d = authenticate(r.deviceCode(), r.deviceSecret());
    Instant now = Instant.now();
    d.heartbeat(r.firmwareVersion(), r.transport(), now);
    DepositSession pending = sessions
      .findByBin_IdAndStatus(d.getBin().getId(), SessionStatus.ACTIVE)
      .filter(session ->
        session.getScanRequestedAt() != null && !session.isExpiredAt(now)
      )
      .orElse(null);
    return HeartbeatResponse.from(d, pending, now);
  }

  @Transactional(readOnly = true)
  public List<DeviceConnectionResponse> listConnections() {
    return devices
      .findAllByOrderByDeviceCodeAsc()
      .stream()
      .map(DeviceConnectionResponse::from)
      .toList();
  }

  @Transactional
  public DeviceConnectionResponse updateConnection(
    UUID binId,
    DeviceConnectionRequest request
  ) {
    Device device = devices
      .findFirstByBin_Id(binId)
      .orElseThrow(() ->
        new NotFoundException("No device is configured for this bin.")
      );
    device.updateConnection(
      request.connectionMode(),
      request.bridgePort(),
      request.acceptedDirection(),
      request.swapDirections()
    );
    return DeviceConnectionResponse.from(device);
  }
}
