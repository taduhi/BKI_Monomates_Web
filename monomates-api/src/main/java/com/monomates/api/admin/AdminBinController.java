package com.monomates.api.admin;

import com.monomates.api.bin.BinService;
import com.monomates.api.bin.dto.*;
import jakarta.validation.Valid;
import java.util.*;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin/bins")
public class AdminBinController {

  private final BinService bins;

  public AdminBinController(BinService b) {
    bins = b;
  }

  @GetMapping
  public List<BinResponse> list() {
    return bins.list(null, null);
  }

  @PostMapping
  public BinResponse create(@Valid @RequestBody SaveBinRequest r) {
    return bins.create(r);
  }

  @PatchMapping("/{id}")
  public BinResponse update(
    @PathVariable UUID id,
    @Valid @RequestBody SaveBinRequest r
  ) {
    return bins.update(id, r);
  }

  @DeleteMapping("/{id}")
  public ResponseEntity<Void> delete(@PathVariable UUID id) {
    bins.delete(id);
    return ResponseEntity.noContent().build();
  }
}
