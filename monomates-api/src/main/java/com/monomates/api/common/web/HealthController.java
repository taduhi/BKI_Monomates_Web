package com.monomates.api.common.web;

import java.time.Instant;
import java.util.Map;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
public class HealthController {

  @GetMapping("/status")
  public Map<String, Object> status() {
    return Map.of(
      "application",
      "MonoMates API",
      "status",
      "running",
      "timestamp",
      Instant.now()
    );
  }
}
