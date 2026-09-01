package com.monomates.api.voucher;

import com.monomates.api.security.CurrentUserService;
import com.monomates.api.voucher.dto.RedemptionResponse;
import java.util.List;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/users/me/redemptions")
public class RedemptionController {

  private final CurrentUserService current;
  private final VoucherService vouchers;

  public RedemptionController(CurrentUserService current, VoucherService vouchers) {
    this.current = current;
    this.vouchers = vouchers;
  }

  @GetMapping
  public List<RedemptionResponse> list(@AuthenticationPrincipal Jwt jwt) {
    return vouchers.listRedemptions(current.require(jwt));
  }
}
