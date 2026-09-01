package com.monomates.api.reward;

import com.monomates.api.deposit.Deposit;
import com.monomates.api.user.UserAccount;
import com.monomates.api.voucher.Redemption;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RewardService {

  private final TokenLedgerRepository ledger;

  public RewardService(TokenLedgerRepository l) {
    ledger = l;
  }

  public int awardDeposit(
    UserAccount u,
    Deposit d,
    boolean valid,
    boolean pet
  ) {
    int awarded = 0;
    int baseTokens = d.getItemType() == null
      ? 1
      : d.getItemType().getBaseTokens();
    int bonusTokens = d.getItemType() == null
      ? 0
      : d.getItemType().getBonusTokens();

    if (valid && baseTokens > 0) {
      ledger.save(
        new TokenLedgerEntry(
          u,
          d,
          null,
          TokenTransactionType.DEPOSIT_BASE,
          baseTokens,
          "Valid deposit at " + d.getSession().getBin().getPublicCode()
        )
      );
      awarded += baseTokens;
    }
    if (valid && pet && bonusTokens > 0) {
      ledger.save(
        new TokenLedgerEntry(
          u,
          d,
          null,
          TokenTransactionType.PET_BONUS,
          bonusTokens,
          "Accepted clear PET bottle bonus"
        )
      );
      awarded += bonusTokens;
    }
    return awarded;
  }

  public void deductForRedemption(UserAccount u, Redemption r) {
    ledger.save(
      new TokenLedgerEntry(
        u,
        null,
        r,
        TokenTransactionType.REDEMPTION,
        -r.getTokenCost(),
        "Voucher redemption: " + r.getVoucher().getTitle()
      )
    );
  }

  @Transactional(readOnly = true)
  public long balance(UserAccount u) {
    return ledger.balance(u.getId());
  }

  @Transactional(readOnly = true)
  public int tokensForDeposit(Deposit d) {
    return Math.toIntExact(ledger.tokensForDeposit(d.getId()));
  }
}
