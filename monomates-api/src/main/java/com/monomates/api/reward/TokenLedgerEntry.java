package com.monomates.api.reward;

import com.monomates.api.common.model.BaseEntity;
import com.monomates.api.deposit.Deposit;
import com.monomates.api.user.UserAccount;
import com.monomates.api.voucher.Redemption;
import jakarta.persistence.*;

@Entity
@Table(name = "token_ledger")
public class TokenLedgerEntry extends BaseEntity {

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "user_id", nullable = false)
  private UserAccount user;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "deposit_id")
  private Deposit deposit;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "redemption_id")
  private Redemption redemption;

  @Enumerated(EnumType.STRING)
  @Column(name = "transaction_type", nullable = false)
  private TokenTransactionType transactionType;

  @Column(nullable = false)
  private int amount;

  @Column(nullable = false)
  private String description;

  protected TokenLedgerEntry() {}

  public TokenLedgerEntry(
    UserAccount u,
    Deposit d,
    Redemption r,
    TokenTransactionType t,
    int a,
    String x
  ) {
    user = u;
    deposit = d;
    redemption = r;
    transactionType = t;
    amount = a;
    description = x;
  }

  public UserAccount getUser() {
    return user;
  }

  public Deposit getDeposit() {
    return deposit;
  }

  public Redemption getRedemption() {
    return redemption;
  }

  public TokenTransactionType getTransactionType() {
    return transactionType;
  }

  public int getAmount() {
    return amount;
  }

  public String getDescription() {
    return description;
  }
}
