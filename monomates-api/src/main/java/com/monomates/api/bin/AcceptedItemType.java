package com.monomates.api.bin;

import com.monomates.api.common.model.BaseEntity;
import jakarta.persistence.*;

@Entity
@Table(name = "accepted_item_types")
public class AcceptedItemType extends BaseEntity {

  @Column(nullable = false, unique = true)
  private String code;

  @Column(nullable = false)
  private String name;

  private String description;

  @Column(name = "base_tokens", nullable = false)
  private int baseTokens;

  @Column(name = "bonus_tokens", nullable = false)
  private int bonusTokens;

  @Column(nullable = false)
  private boolean active;

  protected AcceptedItemType() {}

  public AcceptedItemType(
    String c,
    String n,
    String d,
    int b,
    int x,
    boolean a
  ) {
    code = c;
    name = n;
    description = d;
    baseTokens = b;
    bonusTokens = x;
    active = a;
  }

  public String getCode() {
    return code;
  }

  public String getName() {
    return name;
  }

  public String getDescription() {
    return description;
  }

  public int getBaseTokens() {
    return baseTokens;
  }

  public int getBonusTokens() {
    return bonusTokens;
  }

  public boolean isActive() {
    return active;
  }

  public void update(
    String name,
    String description,
    int baseTokens,
    int bonusTokens,
    boolean active
  ) {
    this.name = name;
    this.description = description;
    this.baseTokens = baseTokens;
    this.bonusTokens = bonusTokens;
    this.active = active;
  }
}
