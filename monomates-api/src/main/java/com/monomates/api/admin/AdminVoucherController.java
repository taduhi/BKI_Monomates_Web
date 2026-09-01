package com.monomates.api.admin;

import com.monomates.api.voucher.*;
import com.monomates.api.voucher.dto.*;
import jakarta.validation.Valid;
import java.util.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin/vouchers")
public class AdminVoucherController {

  private final VoucherRepository repo;
  private final VoucherService service;

  public AdminVoucherController(VoucherRepository r, VoucherService s) {
    repo = r;
    service = s;
  }

  @GetMapping
  public List<VoucherResponse> list() {
    return repo.findAll().stream().map(VoucherResponse::from).toList();
  }

  @PostMapping
  public VoucherResponse create(@Valid @RequestBody SaveVoucherRequest r) {
    return service.create(r);
  }

  @PatchMapping("/{id}")
  public VoucherResponse update(
    @PathVariable UUID id,
    @Valid @RequestBody SaveVoucherRequest r
  ) {
    return service.update(id, r);
  }
}
