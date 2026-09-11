package com.monomates.api.device;

import com.monomates.api.common.exception.*;
import com.monomates.api.deposit.DepositSession;
import com.monomates.api.deposit.DepositSessionRepository;
import com.monomates.api.deposit.SessionStatus;
import com.monomates.api.device.dto.HeartbeatRequest;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
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
    DepositSessionRepository s
  ) {
    devices = d;
    passwords = p;
    sessions = s;
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
  public Map<String, Object> heartbeat(HeartbeatRequest r) {
    Device d = authenticate(r.deviceCode(), r.deviceSecret());
    Instant now = Instant.now();
    d.heartbeat(r.firmwareVersion(), now);
    Map<String, Object> response = new LinkedHashMap<>();
    response.put("status", "accepted");
    response.put("serverTime", now);
    response.put("command", "IDLE");
    sessions
      .findByBin_IdAndStatus(d.getBin().getId(), SessionStatus.ACTIVE)
      .ifPresent(session -> addScanCommand(response, session, now));
    return response;
  }

  private void addScanCommand(
    Map<String, Object> response,
    DepositSession session,
    Instant now
  ) {
    if (session.isExpiredAt(now)) {
      session.expire();
      return;
    }
    if (session.getScanRequestedAt() == null) return;
    response.put("command", "SCAN_ITEM");
    response.put("sessionId", session.getId());
    response.put("requestedAt", session.getScanRequestedAt());
    response.put("expiresAt", session.getExpiresAt());
  }
}
