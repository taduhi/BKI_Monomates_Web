package com.monomates.api.deposit;

import com.monomates.api.bin.*;
import com.monomates.api.common.exception.*;
import com.monomates.api.demo.DemoProperties;
import com.monomates.api.deposit.dto.*;
import com.monomates.api.reward.RewardService;
import com.monomates.api.reward.TokenLedgerRepository;
import com.monomates.api.user.UserAccount;
import java.time.*;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DepositSessionService {

  private final DepositSessionRepository sessions;
  private final DepositRepository deposits;
  private final BinService bins;
  private final DepositProperties p;
  private final RewardService rewards;
  private final DemoProperties demoProps;
  private final TokenLedgerRepository ledger;

  public DepositSessionService(
    DepositSessionRepository s,
    DepositRepository d,
    BinService b,
    DepositProperties p,
    RewardService r,
    DemoProperties demoProps,
    TokenLedgerRepository ledger
  ) {
    sessions = s;
    deposits = d;
    bins = b;
    this.p = p;
    rewards = r;
    this.demoProps = demoProps;
    this.ledger = ledger;
  }

  private static final ZoneId LOCAL_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

  @Transactional
  public SessionResponse start(UserAccount u, String code) {
    RecyclingBin b = bins.requireByCode(code);
    if (b.getStatus() != BinStatus.ACTIVE) throw new BusinessRuleException(
      "This recycling bin is not currently available."
    );
    Instant now = Instant.now();
    sessions
      .findByBin_IdAndStatus(b.getId(), SessionStatus.ACTIVE)
      .ifPresent(existing -> {
        if (existing.isExpiredAt(now)) {
          existing.expire();
          // Hibernate executes inserts before updates during a normal flush.
          // Flush the expired row now so the partial unique index can accept
          // the replacement ACTIVE session in this transaction.
          sessions.saveAndFlush(existing);
        } else {
          throw new ConflictException(
            "This bin already has an active deposit session. Please try again shortly."
          );
        }
      });
    LocalDate today = LocalDate.now(LOCAL_ZONE);
    boolean isDemoAccount =
      demoProps.secretAccountEmail() != null &&
      demoProps.secretAccountEmail().equalsIgnoreCase(u.getEmail());
    if (isDemoAccount) {
      // The fixed demo account is reused for repeated live demonstrations
      // of the QR->session->secret-sort flow, so instead of being blocked
      // by the once-a-day limit below it resets its own record for this
      // bin/day: any previous session (and its deposit/ledger rows, if the
      // earlier run completed) is discarded before starting a fresh one.
      // The DB-level unique constraint on (user, bin, session_date) still
      // applies, so this reset — not just skipping the check — is what
      // makes a second start possible. Every other account is unaffected.
      sessions
        .findByUser_IdAndBin_IdAndSessionDate(u.getId(), b.getId(), today)
        .ifPresent(old -> {
          deposits
            .findBySession_Id(old.getId())
            .ifPresent(d -> ledger.deleteByDeposit_Id(d.getId()));
          deposits.deleteBySession_Id(old.getId());
          sessions.delete(old);
          sessions.flush();
        });
    } else if (
      sessions.existsByUser_IdAndBin_IdAndSessionDate(
        u.getId(),
        b.getId(),
        today
      )
    ) throw new ConflictException(
      "You already started a session for this bin today."
    );
    return SessionResponse.from(
      sessions.save(
        new DepositSession(
          u,
          b,
          now,
          now.plusSeconds(p.sessionSeconds()),
          today
        )
      ),
      null,
      0
    );
  }

  @Transactional
  public SessionResponse getForUser(UserAccount u, UUID id) {
    DepositSession s = ownedForUpdate(u, id);
    refresh(s);
    return response(s);
  }

  @Transactional
  public SessionResponse cancel(UserAccount u, UUID id) {
    DepositSession s = ownedForUpdate(u, id);
    refresh(s);
    if (s.getStatus() != SessionStatus.ACTIVE) throw new BusinessRuleException(
      "Only an active session can be cancelled."
    );
    s.cancel();
    return response(s);
  }

  private DepositSession ownedForUpdate(UserAccount u, UUID id) {
    DepositSession s = sessions
      .findByIdForUpdate(id)
      .orElseThrow(() ->
        new NotFoundException("Deposit session was not found.")
      );
    if (!s.getUser().getId().equals(u.getId())) throw new NotFoundException(
      "Deposit session was not found."
    );
    return s;
  }

  private void refresh(DepositSession s) {
    if (s.isExpiredAt(Instant.now())) s.expire();
  }

  private SessionResponse response(DepositSession s) {
    return deposits
      .findBySession_Id(s.getId())
      .map(d -> {
        int t = rewards.tokensForDeposit(d);
        return SessionResponse.from(s, DepositResponse.from(d, t), t);
      })
      .orElseGet(() -> SessionResponse.from(s, null, 0));
  }
}
