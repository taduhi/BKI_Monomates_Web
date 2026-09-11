package com.monomates.api.admin;

import com.monomates.api.device.DeviceService;
import com.monomates.api.device.dto.DeviceConnectionRequest;
import com.monomates.api.device.dto.DeviceConnectionResponse;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin/devices")
public class AdminDeviceController {

  private final DeviceService devices;

  public AdminDeviceController(DeviceService devices) {
    this.devices = devices;
  }

  @GetMapping
  public List<DeviceConnectionResponse> list() {
    return devices.listConnections();
  }

  @PatchMapping("/{binId}/connection")
  public DeviceConnectionResponse updateConnection(
    @PathVariable UUID binId,
    @Valid @RequestBody DeviceConnectionRequest request
  ) {
    return devices.updateConnection(binId, request);
  }
}
