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
    DepositSession existingOnThisBin = sessions
      .findByBin_IdAndStatus(b.getId(), SessionStatus.ACTIVE)
      .orElse(null);
    if (existingOnThisBin != null) {
      if (existingOnThisBin.isExpiredAt(now)) {
        existingOnThisBin.expire();
        // Hibernate executes inserts before updates during a normal flush.
        // Flush the expired row now so the partial unique index can accept
        // the replacement ACTIVE session in this transaction.
        sessions.saveAndFlush(existingOnThisBin);
      } else {
        // There is only one physical rig for this bin. Rather than fail,
        // attach the caller to the session already in progress — whoever
        // started it keeps ownership (and the reward), but anyone can now
        // watch it resolve in real time. This is what lets the fixed demo
        // account "stand behind" a real user's deposit: it opens the same
        // bin, sees this exact in-progress session, and its secret controls
        // can still resolve it (DepositProcessingService.processDemo no
        // longer requires ownership).
        return response(existingOnThisBin);
      }
    }
    // Only one deposit can be in progress across the whole system at a
    // time — there is only one physical sorting rig being operated, not
    // one per bin. A different bin's still-active session blocks a new one
    // here; if it has quietly expired, clear it and proceed instead.
    DepositSession activeElsewhere = sessions
      .findFirstByStatus(SessionStatus.ACTIVE)
      .orElse(null);
    if (activeElsewhere != null) {
      if (activeElsewhere.isExpiredAt(now)) {
        activeElsewhere.expire();
        sessions.saveAndFlush(activeElsewhere);
      } else {
        throw new ConflictException(
          "Bin " +
          activeElsewhere.getBin().getPublicCode() +
          " currently has a deposit in progress. Please wait until it finishes."
        );
      }
    }
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

  // Deliberately not ownership-restricted: with only one deposit ever in
  // progress system-wide, anyone signed in may watch it resolve (there is
  // nothing sensitive in a session's status/timing). Actions that represent
  // the depositor's own physical steps (cancel, requestScan below) stay
  // owner-only; only viewing is open.
  @Transactional
  public SessionResponse getForUser(UserAccount u, UUID id) {
    DepositSession s = sessions
      .findByIdForUpdate(id)
      .orElseThrow(() -> new NotFoundException("Deposit session was not found."));
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
    s.requestScan(Instant.now(), p.sessionSeconds());
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
