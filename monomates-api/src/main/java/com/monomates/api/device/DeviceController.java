package com.monomates.api.device;

import com.monomates.api.deposit.*;
import com.monomates.api.deposit.dto.DepositResponse;
import com.monomates.api.device.dto.*;
import jakarta.validation.Valid;
import java.util.Map;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/device")
public class DeviceController {

  private final DepositProcessingService deposits;
  private final DeviceService devices;

  public DeviceController(DepositProcessingService d, DeviceService s) {
    deposits = d;
    devices = s;
  }

  @PostMapping("/events/deposit")
  public DepositResponse deposit(
    @Valid @RequestBody DeviceDepositEventRequest r
  ) {
    return deposits.process(r);
  }

  @PostMapping("/heartbeat")
  public Map<String, Object> heartbeat(@Valid @RequestBody HeartbeatRequest r) {
    return devices.heartbeat(r);
  }
}
