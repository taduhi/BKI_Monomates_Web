package com.monomates.api.deposit;

import com.monomates.api.bin.*;
import com.monomates.api.common.exception.*;
import com.monomates.api.demo.DemoProperties;
import com.monomates.api.deposit.dto.*;
import com.monomates.api.reward.RewardService;
import com.monomates.api.user.UserAccount;
import com.monomates.api.user.UserRole;
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

  public DepositSessionService(
    DepositSessionRepository s,
    DepositRepository d,
    BinService b,
    DepositProperties p,
    RewardService r,
    DemoProperties demoProps
  ) {
    sessions = s;
    deposits = d;
    bins = b;
    this.p = p;
    rewards = r;
    this.demoProps = demoProps;
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
    boolean isExemptFromDailyLimit =
      isDemoAccount || u.getRole() == UserRole.ADMIN;
    // The fixed demo account and any admin account have no daily limit at
    // all — demo for unlimited live demonstrations, admin so staff can test
    // the full deposit flow without burning through the same 10-scan
    // allowance a regular citizen gets. Every other account may start at
    // most app.deposit.daily-scan-limit sessions per day, counted across
    // every bin (not per bin) — tokens from earlier scans the same day are
    // kept, never discarded. "Today" is the current date in
    // Asia/Ho_Chi_Minh, recomputed from the real clock on every request, so
    // this limit lifts on its own at local midnight with no scheduled job.
    if (!isExemptFromDailyLimit) {
      long usedToday = sessions.countByUser_IdAndSessionDate(
        u.getId(),
        today
      );
      if (usedToday >= p.dailyScanLimit()) throw new ConflictException(
        "You have used all " +
        p.dailyScanLimit() +
        " scans for today. Try again tomorrow."
      );
    }
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

  @Transactional
  public SessionResponse requestScan(UserAccount u, UUID id) {
    DepositSession s = ownedForUpdate(u, id);
    refresh(s);
    if (s.getStatus() != SessionStatus.ACTIVE) throw new BusinessRuleException(
      "Only an active session can scan an item."
    );
    s.requestScan(Instant.now());
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
