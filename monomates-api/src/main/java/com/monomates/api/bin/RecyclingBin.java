package com.monomates.api.bin;

import com.monomates.api.common.model.BaseEntity;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.*;

@Entity
@Table(name = "bins")
public class RecyclingBin extends BaseEntity {

  @Column(name = "public_code", nullable = false, unique = true)
  private String publicCode;

  @Column(nullable = false)
  private String name;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "location_id", nullable = false)
  private Location location;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private BinStatus status;

  @Column(name = "capacity_percent", nullable = false)
  private int capacityPercent;

  @Column(name = "last_seen_at")
  private Instant lastSeenAt;

  @ManyToMany(fetch = FetchType.LAZY)
  @JoinTable(
    name = "bin_accepted_items",
    joinColumns = @JoinColumn(name = "bin_id"),
    inverseJoinColumns = @JoinColumn(name = "item_type_id")
  )
  private Set<AcceptedItemType> acceptedItems = new LinkedHashSet<>();

  protected RecyclingBin() {}

  public RecyclingBin(String c, String n, Location l, BinStatus s, int p) {
    publicCode = c;
    name = n;
    location = l;
    status = s;
    capacityPercent = p;
  }

  public String getPublicCode() {
    return publicCode;
  }

  public String getName() {
    return name;
  }

  public Location getLocation() {
    return location;
  }

  public BinStatus getStatus() {
    return status;
  }

  public int getCapacityPercent() {
    return capacityPercent;
  }

  public Instant getLastSeenAt() {
    return lastSeenAt;
  }

  public Set<AcceptedItemType> getAcceptedItems() {
    return acceptedItems;
  }

  public void addAcceptedItem(AcceptedItemType i) {
    acceptedItems.add(i);
  }

  public void replaceAcceptedItems(Set<AcceptedItemType> i) {
    acceptedItems.clear();
    acceptedItems.addAll(i);
  }

  public void update(String n, BinStatus s, int p) {
    name = n;
    status = s;
    capacityPercent = p;
  }

  public void markSeen(Instant i) {
    lastSeenAt = i;
  }
}
