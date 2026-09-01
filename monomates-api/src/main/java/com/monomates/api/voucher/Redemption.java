package com.monomates.api.voucher;

import com.monomates.api.common.model.BaseEntity;
import com.monomates.api.user.UserAccount;
import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "redemptions")
public class Redemption extends BaseEntity {

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "user_id", nullable = false)
  private UserAccount user;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "voucher_id", nullable = false)
  private Voucher voucher;

  @Column(name = "token_cost", nullable = false)
  private int tokenCost;

  @Column(name = "redemption_code", nullable = false, unique = true)
  private String redemptionCode;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private RedemptionStatus status;

  @Column(name = "used_at")
  private Instant usedAt;

  protected Redemption() {}

  public Redemption(UserAccount u, Voucher v, int c, String code) {
    user = u;
    voucher = v;
    tokenCost = c;
    redemptionCode = code;
    status = RedemptionStatus.ISSUED;
  }

  public UserAccount getUser() {
    return user;
  }

  public Voucher getVoucher() {
    return voucher;
  }

  public int getTokenCost() {
    return tokenCost;
  }

  public String getRedemptionCode() {
    return redemptionCode;
  }

  public RedemptionStatus getStatus() {
    return status;
  }

  public Instant getUsedAt() {
    return usedAt;
  }
}
