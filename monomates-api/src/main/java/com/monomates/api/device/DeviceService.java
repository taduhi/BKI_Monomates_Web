package com.monomates.api.device;

import com.monomates.api.common.exception.*;
import com.monomates.api.device.dto.HeartbeatRequest;
import java.time.Instant;
import java.util.Map;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DeviceService {

  private final DeviceRepository devices;
  private final PasswordEncoder passwords;

  public DeviceService(DeviceRepository d, PasswordEncoder p) {
    devices = d;
    passwords = p;
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
    d.heartbeat(r.firmwareVersion(), Instant.now());
    return Map.of("status", "accepted", "serverTime", Instant.now());
  }
}
