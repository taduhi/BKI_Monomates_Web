package com.monomates.api.voucher;

import com.monomates.api.security.CurrentUserService;
import com.monomates.api.voucher.dto.*;
import java.util.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/vouchers")
public class VoucherController {

  private final VoucherService vouchers;
  private final CurrentUserService current;

  public VoucherController(VoucherService v, CurrentUserService c) {
    vouchers = v;
    current = c;
  }

  @GetMapping
  public List<VoucherResponse> list() {
    return vouchers.listAvailable();
  }

  @PostMapping("/{voucherId}/redeem")
  public RedemptionResponse redeem(
    @AuthenticationPrincipal Jwt j,
    @PathVariable UUID voucherId
  ) {
    return vouchers.redeem(current.require(j), voucherId);
  }
}
