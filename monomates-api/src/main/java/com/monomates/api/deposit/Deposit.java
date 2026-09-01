package com.monomates.api.deposit;

import com.monomates.api.bin.AcceptedItemType;
import com.monomates.api.common.model.BaseEntity;
import com.monomates.api.device.DeviceEvent;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "deposits")
public class Deposit extends BaseEntity {

  @OneToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "session_id", nullable = false, unique = true)
  private DepositSession session;

  @OneToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "device_event_id", nullable = false, unique = true)
  private DeviceEvent deviceEvent;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "item_type_id")
  private AcceptedItemType itemType;

  @Enumerated(EnumType.STRING)
  @Column(name = "verification_status", nullable = false)
  private DepositStatus verificationStatus;

  @Column(name = "ir_detected", nullable = false)
  private boolean irDetected;

  @Column(name = "measured_weight_grams")
  private BigDecimal measuredWeightGrams;

  @Column(name = "classification_confidence")
  private BigDecimal classificationConfidence;

  @Column(name = "rejection_reason")
  private String rejectionReason;

  @Column(name = "verified_at", nullable = false)
  private Instant verifiedAt;

  protected Deposit() {}

  public Deposit(
    DepositSession s,
    DeviceEvent e,
    AcceptedItemType i,
    DepositStatus st,
    boolean ir,
    BigDecimal w,
    BigDecimal c,
    String r,
    Instant v
  ) {
    session = s;
    deviceEvent = e;
    itemType = i;
    verificationStatus = st;
    irDetected = ir;
    measuredWeightGrams = w;
    classificationConfidence = c;
    rejectionReason = r;
    verifiedAt = v;
  }

  public DepositSession getSession() {
    return session;
  }

  public DeviceEvent getDeviceEvent() {
    return deviceEvent;
  }

  public AcceptedItemType getItemType() {
    return itemType;
  }

  public DepositStatus getVerificationStatus() {
    return verificationStatus;
  }

  public BigDecimal getMeasuredWeightGrams() {
    return measuredWeightGrams;
  }

  public BigDecimal getClassificationConfidence() {
    return classificationConfidence;
  }

  public String getRejectionReason() {
    return rejectionReason;
  }

  public Instant getVerifiedAt() {
    return verifiedAt;
  }
}
