package com.monomates.api.voucher;

import com.monomates.api.common.exception.*;
import com.monomates.api.reward.RewardService;
import com.monomates.api.user.UserAccount;
import com.monomates.api.user.UserRepository;
import com.monomates.api.voucher.dto.*;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class VoucherService {

  private static final String ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
  private final VoucherRepository vouchers;
  private final RedemptionRepository redemptions;
  private final RewardService rewards;
  private final UserRepository users;
  private final SecureRandom random = new SecureRandom();

  public VoucherService(
    VoucherRepository v,
    RedemptionRepository r,
    RewardService x,
    UserRepository u
  ) {
    vouchers = v;
    redemptions = r;
    rewards = x;
    users = u;
  }

  @Transactional(readOnly = true)
  public List<VoucherResponse> listAvailable() {
    Instant n = Instant.now();
    return vouchers
      .findAll()
      .stream()
      .filter(v -> v.isAvailable(n))
      .map(VoucherResponse::from)
      .toList();
  }

  @Transactional(readOnly = true)
  public List<RedemptionResponse> listRedemptions(UserAccount user) {
    return redemptions
      .findByUser_IdOrderByCreatedAtDesc(user.getId())
      .stream()
      .map(RedemptionResponse::from)
      .toList();
  }

  /**
   * Lock order is always USER then VOUCHER. Locking the user first — by
   * re-fetching with a pessimistic write lock instead of trusting the
   * caller-supplied entity — serializes every redemption a given user makes,
   * even across two different vouchers, closing the double-spend race where
   * two concurrent redemptions could both read a balance that only covers
   * one of them. Reversing this order (voucher first) would only serialize
   * redemptions of the SAME voucher and would risk deadlock against any
   * future path that locks a user first.
   */
  @Transactional
  public RedemptionResponse redeem(UserAccount caller, UUID id) {
    UserAccount u = users
      .findByIdForUpdate(caller.getId())
      .orElseThrow(() ->
        new NotFoundException("Authenticated user was not found.")
      );
    Voucher v = vouchers
      .findByIdForUpdate(id)
      .orElseThrow(() -> new NotFoundException("Voucher was not found."));
    if (!v.isAvailable(Instant.now())) throw new BusinessRuleException(
      "This voucher is not currently available."
    );
    long previousRedemptions = redemptions.countByUser_IdAndVoucher_Id(
      u.getId(),
      v.getId()
    );
    if (previousRedemptions >= v.getMaxRedemptionsPerUser()) {
      throw new BusinessRuleException(
        "You have reached the redemption limit for this voucher."
      );
    }
    long b = rewards.balance(u);
    if (b < v.getTokenCost()) throw new BusinessRuleException(
      "Your token balance is not sufficient for this voucher."
    );
    v.decrementInventory();
    Redemption r = redemptions.save(
      new Redemption(u, v, v.getTokenCost(), code())
    );
    rewards.deductForRedemption(u, r);
    return RedemptionResponse.from(r, b - v.getTokenCost());
  }

  @Transactional
  public VoucherResponse create(SaveVoucherRequest r) {
    validate(r);
    return VoucherResponse.from(
      vouchers.save(
        new Voucher(
          r.partnerName().trim(),
          r.title().trim(),
          r.description(),
          r.tokenCost(),
          r.inventory(),
          r.validFrom(),
          r.validUntil(),
          r.status(),
          r.imageUrl(),
          clean(r.termsAndConditions()),
          instructions(r),
          displayText(r),
          maxPerUser(r)
        )
      )
    );
  }

  @Transactional
  public VoucherResponse update(UUID id, SaveVoucherRequest r) {
    validate(r);
    Voucher v = vouchers
      .findByIdForUpdate(id)
      .orElseThrow(() -> new NotFoundException("Voucher was not found."));
    v.update(
      r.partnerName().trim(),
      r.title().trim(),
      r.description(),
      r.tokenCost(),
      r.inventory(),
      r.validFrom(),
      r.validUntil(),
      r.status(),
      r.imageUrl(),
      clean(r.termsAndConditions()),
      instructions(r),
      displayText(r),
      maxPerUser(r)
    );
    return VoucherResponse.from(v);
  }

  private String code() {
    StringBuilder s = new StringBuilder("MM-");
    for (int i = 0; i < 10; i++) s.append(
      ALPHABET.charAt(random.nextInt(ALPHABET.length()))
    );
    return s.toString();
  }

  private void validate(SaveVoucherRequest r) {
    if (
      r.validFrom() != null &&
      r.validUntil() != null &&
      !r.validUntil().isAfter(r.validFrom())
    ) throw new BusinessRuleException(
      "Voucher expiry must be later than its start date."
    );
  }

  private String clean(String value) {
    return value == null || value.isBlank() ? null : value.trim();
  }

  private int maxPerUser(SaveVoucherRequest r) {
    return r.maxRedemptionsPerUser() == null
      ? 1
      : r.maxRedemptionsPerUser();
  }

  private String instructions(SaveVoucherRequest r) {
    String value = clean(r.redemptionInstructions());
    return value == null
      ? "Show the issued code at the partner counter."
      : value;
  }

  private String displayText(SaveVoucherRequest r) {
    String value = clean(r.redemptionDisplayText());
    return value == null ? r.title().trim() : value;
  }
}
