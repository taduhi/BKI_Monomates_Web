package com.monomates.api.deposit;

import com.monomates.api.bin.RecyclingBin;
import com.monomates.api.common.model.BaseEntity;
import com.monomates.api.user.UserAccount;
import jakarta.persistence.*;
import java.time.*;

@Entity
@Table(name = "deposit_sessions")
public class DepositSession extends BaseEntity {

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "user_id", nullable = false)
  private UserAccount user;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "bin_id", nullable = false)
  private RecyclingBin bin;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private SessionStatus status;

  @Column(name = "session_date", nullable = false)
  private LocalDate sessionDate;

  @Column(name = "started_at", nullable = false)
  private Instant startedAt;

  @Column(name = "expires_at", nullable = false)
  private Instant expiresAt;

  @Column(name = "completed_at")
  private Instant completedAt;

  @Column(name = "scan_requested_at")
  private Instant scanRequestedAt;

  protected DepositSession() {}

  public DepositSession(
    UserAccount u,
    RecyclingBin b,
    Instant s,
    Instant e,
    LocalDate d
  ) {
    user = u;
    bin = b;
    startedAt = s;
    expiresAt = e;
    sessionDate = d;
    status = SessionStatus.ACTIVE;
  }

  public UserAccount getUser() {
    return user;
  }

  public RecyclingBin getBin() {
    return bin;
  }

  public SessionStatus getStatus() {
    return status;
  }

  public LocalDate getSessionDate() {
    return sessionDate;
  }

  public Instant getStartedAt() {
    return startedAt;
  }

  public Instant getExpiresAt() {
    return expiresAt;
  }

  public Instant getCompletedAt() {
    return completedAt;
  }

  public Instant getScanRequestedAt() {
    return scanRequestedAt;
  }

  public void requestScan(Instant requestedAt) {
    if (status == SessionStatus.ACTIVE && scanRequestedAt == null) {
      scanRequestedAt = requestedAt;
    }
  }

  public boolean isExpiredAt(Instant n) {
    return status == SessionStatus.ACTIVE && !expiresAt.isAfter(n);
  }

  public void expire() {
    if (status == SessionStatus.ACTIVE) status = SessionStatus.EXPIRED;
  }

  public void cancel() {
    if (status == SessionStatus.ACTIVE) status = SessionStatus.CANCELLED;
  }

  public void complete(Instant n) {
    status = SessionStatus.COMPLETED;
    completedAt = n;
  }

  public void reject(Instant n) {
    status = SessionStatus.REJECTED;
    completedAt = n;
  }
}
