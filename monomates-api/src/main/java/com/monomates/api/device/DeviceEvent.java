package com.monomates.api.device;

import com.monomates.api.common.model.BaseEntity;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "device_events")
public class DeviceEvent extends BaseEntity {

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "device_id", nullable = false)
  private Device device;

  @Column(name = "external_event_id", nullable = false)
  private String externalEventId;

  @Column(name = "event_type", nullable = false)
  private String eventType;

  @Column(name = "recorded_at", nullable = false)
  private Instant recordedAt;

  @Column(name = "received_at", nullable = false)
  private Instant receivedAt;

  @Column(name = "ir_detected", nullable = false)
  private boolean irDetected;

  @Column(name = "weight_change_grams")
  private BigDecimal weightChangeGrams;

  @Column(name = "claimed_item_code")
  private String claimedItemCode;

  @Column(name = "classification_confidence")
  private BigDecimal classificationConfidence;

  @Column(name = "payload_json", columnDefinition = "text")
  private String payloadJson;

  @Enumerated(EnumType.STRING)
  @Column(name = "processing_status", nullable = false)
  private EventProcessingStatus processingStatus;

  protected DeviceEvent() {}

  public DeviceEvent(
    Device d,
    String id,
    Instant recorded,
    Instant received,
    boolean ir,
    BigDecimal weight,
    String item,
    BigDecimal confidence,
    String payload
  ) {
    device = d;
    externalEventId = id;
    eventType = "DEPOSIT";
    recordedAt = recorded;
    receivedAt = received;
    irDetected = ir;
    weightChangeGrams = weight;
    claimedItemCode = item;
    classificationConfidence = confidence;
    payloadJson = payload;
    processingStatus = EventProcessingStatus.RECEIVED;
  }

  public Device getDevice() {
    return device;
  }

  public void markProcessed() {
    processingStatus = EventProcessingStatus.PROCESSED;
  }

  public void markRejected() {
    processingStatus = EventProcessingStatus.REJECTED;
  }
}
