package com.monomates.api.voucher;

import com.monomates.api.common.model.BaseEntity;
import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "vouchers")
public class Voucher extends BaseEntity {

  @Column(name = "partner_name", nullable = false)
  private String partnerName;

  @Column(nullable = false)
  private String title;

  @Column(length = 800)
  private String description;

  @Column(name = "token_cost", nullable = false)
  private int tokenCost;

  @Column(nullable = false)
  private int inventory;

  @Column(name = "valid_from")
  private Instant validFrom;

  @Column(name = "valid_until")
  private Instant validUntil;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private VoucherStatus status;

  @Column(name = "image_url", length = 1000)
  private String imageUrl;

  @Column(name = "terms_and_conditions", length = 2000)
  private String termsAndConditions;

  @Column(name = "redemption_instructions", length = 1000)
  private String redemptionInstructions;

  @Column(name = "redemption_display_text", length = 300)
  private String redemptionDisplayText;

  @Column(name = "max_redemptions_per_user", nullable = false)
  private int maxRedemptionsPerUser;

  protected Voucher() {}

  public Voucher(
    String p,
    String t,
    String d,
    int c,
    int i,
    Instant f,
    Instant u,
    VoucherStatus s,
    String image,
    String terms,
    String instructions,
    String displayText,
    int maxPerUser
  ) {
    partnerName = p;
    title = t;
    description = d;
    tokenCost = c;
    inventory = i;
    validFrom = f;
    validUntil = u;
    status = s;
    imageUrl = image;
    termsAndConditions = terms;
    redemptionInstructions = instructions;
    redemptionDisplayText = displayText;
    maxRedemptionsPerUser = maxPerUser;
  }

  public String getPartnerName() {
    return partnerName;
  }

  public String getTitle() {
    return title;
  }

  public String getDescription() {
    return description;
  }

  public int getTokenCost() {
    return tokenCost;
  }

  public int getInventory() {
    return inventory;
  }

  public Instant getValidFrom() {
    return validFrom;
  }

  public Instant getValidUntil() {
    return validUntil;
  }

  public VoucherStatus getStatus() {
    return status;
  }

  public String getImageUrl() {
    return imageUrl;
  }

  public String getTermsAndConditions() {
    return termsAndConditions;
  }

  public String getRedemptionInstructions() {
    return redemptionInstructions;
  }

  public String getRedemptionDisplayText() {
    return redemptionDisplayText;
  }

  public int getMaxRedemptionsPerUser() {
    return maxRedemptionsPerUser;
  }

  public boolean isAvailable(Instant n) {
    return (
      status == VoucherStatus.ACTIVE &&
      inventory > 0 &&
      (validFrom == null || !validFrom.isAfter(n)) &&
      (validUntil == null || validUntil.isAfter(n))
    );
  }

  public void decrementInventory() {
    inventory--;
  }

  public void update(
    String p,
    String t,
    String d,
    int c,
    int i,
    Instant f,
    Instant u,
    VoucherStatus s,
    String image,
    String terms,
    String instructions,
    String displayText,
    int maxPerUser
  ) {
    partnerName = p;
    title = t;
    description = d;
    tokenCost = c;
    inventory = i;
    validFrom = f;
    validUntil = u;
    status = s;
    imageUrl = image;
    termsAndConditions = terms;
    redemptionInstructions = instructions;
    redemptionDisplayText = displayText;
    maxRedemptionsPerUser = maxPerUser;
  }
}
