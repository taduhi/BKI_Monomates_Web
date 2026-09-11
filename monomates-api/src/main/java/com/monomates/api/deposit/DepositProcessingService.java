package com.monomates.api.deposit;

import com.monomates.api.bin.*;
import com.monomates.api.common.exception.*;
import com.monomates.api.deposit.dto.DepositResponse;
import com.monomates.api.device.*;
import com.monomates.api.device.dto.DeviceDepositEventRequest;
import com.monomates.api.reward.RewardService;
import com.monomates.api.user.UserAccount;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DepositProcessingService {

  private final DeviceService deviceService;
  private final DeviceRepository devices;
  private final DeviceEventRepository events;
  private final DepositSessionRepository sessions;
  private final DepositRepository deposits;
  private final AcceptedItemTypeRepository items;
  private final DepositProperties p;
  private final RewardService rewards;

  public DepositProcessingService(
    DeviceService ds,
    DeviceRepository d,
    DeviceEventRepository e,
    DepositSessionRepository s,
    DepositRepository dp,
    AcceptedItemTypeRepository i,
    DepositProperties p,
    RewardService r
  ) {
    deviceService = ds;
    devices = d;
    events = e;
    sessions = s;
    deposits = dp;
    items = i;
    this.p = p;
    rewards = r;
  }

  @Transactional
  public DepositResponse process(DeviceDepositEventRequest r) {
    return internal(
      deviceService.authenticate(r.deviceCode(), r.deviceSecret()),
      r
    );
  }

  @Transactional
  public DepositResponse processDemo(
    UserAccount user,
    UUID sessionId,
    DemoOutcome outcome
  ) {
    // Deliberately not ownership-restricted: the fixed demo account stands
    // in for the physical hardware/camera, which resolves whichever deposit
    // is actually in progress, not only one it happens to own itself. Since
    // only one session can be ACTIVE system-wide (DepositSessionService.
    // start), this can never resolve the "wrong" person's deposit.
    DepositSession s = sessions
      .findById(sessionId)
      .orElseThrow(() ->
        new NotFoundException("Deposit session was not found.")
      );
    s.requestScan(Instant.now(), p.sessionSeconds());
    Device d = devices
      .findFirstByBin_Id(s.getBin().getId())
      .orElseThrow(() ->
        new NotFoundException("No demo device is configured for this bin.")
      );
    DeviceDepositEventRequest r = switch (outcome) {
      case ACCEPTED_PET -> new DeviceDepositEventRequest(
        d.getDeviceCode(),
        "demo",
        "demo-" + UUID.randomUUID(),
        sessionId,
        true,
        new BigDecimal("24.0"),
        "CLEAR_PET_BOTTLE",
        new BigDecimal("0.95"),
        Instant.now(),
        "{\"source\":\"local-demo\"}"
      );
      case VALID_UNCERTAIN -> new DeviceDepositEventRequest(
        d.getDeviceCode(),
        "demo",
        "demo-" + UUID.randomUUID(),
        sessionId,
        true,
        new BigDecimal("18.0"),
        "CLEAR_PET_BOTTLE",
        new BigDecimal("0.40"),
        Instant.now(),
        "{\"source\":\"local-demo\"}"
      );
      case REJECTED -> new DeviceDepositEventRequest(
        d.getDeviceCode(),
        "demo",
        "demo-" + UUID.randomUUID(),
        sessionId,
        false,
        BigDecimal.ZERO,
        null,
        null,
        Instant.now(),
        "{\"source\":\"local-demo\"}"
      );
    };
    return internal(d, r);
  }

  /**
   * A duplicate device event (hardware retry, or the same request replayed)
   * targets the same deposit session as the original. `findByIdForUpdate`
   * takes a pessimistic row lock on that session, so two concurrent calls
   * for the same event are fully serialized here: whichever request arrives
   * second only proceeds after the first has committed, and immediately
   * finds the event/deposit the first request already created. Checking
   * for that existing event before the session-active check (rather than
   * only as a pre-transaction fast path) is what keeps the second request
   * idempotent instead of failing with "session is not active".
   */
  private DepositResponse internal(Device device, DeviceDepositEventRequest r) {
    DepositSession s = resolveSession(device, r.sessionId());

    Optional<DeviceEvent> dup = events.findByDevice_IdAndExternalEventId(
      device.getId(),
      r.eventId()
    );
    if (dup.isPresent()) {
      Deposit d = deposits
        .findByDeviceEvent_Id(dup.get().getId())
        .orElseThrow(() ->
          new BusinessRuleException(
            "This event has already been received and is still being processed."
          )
        );
      if (!d.getSession().getId().equals(s.getId())) {
        throw new ConflictException(
          "This event ID is already linked to another deposit session."
        );
      }
      return DepositResponse.from(d, rewards.tokensForDeposit(d));
    }

    Instant now = Instant.now();
    if (
      !s.getBin().getId().equals(device.getBin().getId())
    ) throw new BusinessRuleException(
      "The device and deposit session belong to different bins."
    );
    if (s.isExpiredAt(now)) {
      s.expire();
      throw new BusinessRuleException("The deposit session has expired.");
    }
    if (s.getStatus() != SessionStatus.ACTIVE) throw new BusinessRuleException(
      "The deposit session is not active."
    );
    if (s.getScanRequestedAt() == null) throw new BusinessRuleException(
      "An item scan has not been requested for this session."
    );
    DeviceEvent e = events.save(
      new DeviceEvent(
        device,
        r.eventId(),
        r.recordedAt() == null ? now : r.recordedAt(),
        now,
        r.irDetected(),
        r.weightChangeGrams(),
        normalize(r.itemType()),
        r.classificationConfidence(),
        r.rawPayload()
      )
    );
    boolean valid =
      r.irDetected() &&
      r.weightChangeGrams() != null &&
      r.weightChangeGrams().compareTo(p.minimumWeightGrams()) >= 0;
    AcceptedItemType item = accepted(device, r.itemType());
    boolean confidence =
      r.classificationConfidence() == null ||
      r
        .classificationConfidence()
        .compareTo(p.minimumClassificationConfidence()) >= 0;
    boolean pet =
      valid &&
      item != null &&
      "CLEAR_PET_BOTTLE".equalsIgnoreCase(item.getCode()) &&
      confidence;
    DepositStatus status = !valid
      ? DepositStatus.REJECTED
      : pet
        ? DepositStatus.ACCEPTED
        : DepositStatus.VALID_UNCLASSIFIED;
    String reason = valid
      ? null
      : "IR and minimum weight conditions were not both satisfied.";
    Deposit d = deposits.save(
      new Deposit(
        s,
        e,
        item,
        status,
        r.irDetected(),
        r.weightChangeGrams(),
        r.classificationConfidence(),
        reason,
        now
      )
    );
    int tokens = rewards.awardDeposit(s.getUser(), d, valid, pet);
    if (valid) {
      s.complete(now);
      e.markProcessed();
    } else {
      s.reject(now);
      e.markRejected();
    }
    return DepositResponse.from(d, tokens);
  }

  /**
   * A browser-driven caller (the demo/testing endpoints) already knows the
   * session it started and passes its id directly. Real bin hardware never
   * learns a session id at all — only the user's own phone/browser does —
   * so per the original hardware-integration design it identifies itself by
   * device/bin only, and this finds whichever session is currently ACTIVE
   * for that bin instead.
   */
  private DepositSession resolveSession(Device device, UUID sessionId) {
    if (sessionId != null) {
      return sessions
        .findByIdForUpdate(sessionId)
        .orElseThrow(() ->
          new NotFoundException("Deposit session was not found.")
        );
    }
    return sessions
      .findByBin_IdAndStatus(device.getBin().getId(), SessionStatus.ACTIVE)
      .orElseThrow(() ->
        new NotFoundException("No active deposit session for this bin.")
      );
  }

  private AcceptedItemType accepted(Device d, String code) {
    if (code == null || code.isBlank()) return null;
    AcceptedItemType i = items.findByCodeIgnoreCase(code.trim()).orElse(null);
    if (i == null || !i.isActive()) return null;
    return d
      .getBin()
      .getAcceptedItems()
      .stream()
      .anyMatch(x -> x.getId().equals(i.getId()))
      ? i
      : null;
  }

  private String normalize(String v) {
    return v == null ? null : v.trim().toUpperCase(Locale.ROOT);
  }

  public enum DemoOutcome {
    ACCEPTED_PET,
    VALID_UNCERTAIN,
    REJECTED,
  }
}
