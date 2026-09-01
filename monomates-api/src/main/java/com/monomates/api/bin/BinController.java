package com.monomates.api.bin;

import com.monomates.api.bin.dto.BinResponse;
import java.util.List;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/bins")
public class BinController {

  private final BinService bins;

  public BinController(BinService b) {
    bins = b;
  }

  @GetMapping
  public List<BinResponse> list(
    @RequestParam(required = false) String q,
    @RequestParam(required = false) BinStatus status
  ) {
    return bins.list(q, status);
  }

  @GetMapping("/{publicCode}")
  public BinResponse get(@PathVariable String publicCode) {
    return bins.get(publicCode);
  }
}
