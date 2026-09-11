package com.monomates.api.bin;

import com.monomates.api.bin.dto.*;
import com.monomates.api.common.exception.*;
import java.util.*;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BinService {

  private final RecyclingBinRepository bins;
  private final LocationRepository locations;
  private final AcceptedItemTypeRepository items;

  public BinService(
    RecyclingBinRepository b,
    LocationRepository l,
    AcceptedItemTypeRepository i
  ) {
    bins = b;
    locations = l;
    items = i;
  }

  @Transactional(readOnly = true)
  public List<BinResponse> list(String q, BinStatus s) {
    String n = q == null ? "" : q.trim().toLowerCase(Locale.ROOT);
    return bins
      .findAll()
      .stream()
      .filter(b -> s == null || b.getStatus() == s)
      .filter(
        b ->
          n.isBlank() ||
          b.getName().toLowerCase(Locale.ROOT).contains(n) ||
          b.getPublicCode().toLowerCase(Locale.ROOT).contains(n) ||
          b.getLocation().getAddress().toLowerCase(Locale.ROOT).contains(n)
      )
      .map(BinResponse::from)
      .toList();
  }

  @Transactional(readOnly = true)
  public BinResponse get(String c) {
    return BinResponse.from(requireByCode(c));
  }

  @Transactional(readOnly = true)
  public RecyclingBin requireByCode(String c) {
    return bins
      .findByPublicCodeIgnoreCase(c)
      .orElseThrow(() -> new NotFoundException("Recycling bin was not found."));
  }

  @Transactional
  public BinResponse create(SaveBinRequest r) {
    if (
      bins.existsByPublicCodeIgnoreCase(r.publicCode())
    ) throw new ConflictException("A bin already uses this public code.");
    Location l = locations.save(
      new Location(
        r.locationName().trim(),
        r.address().trim(),
        r.latitude(),
        r.longitude()
      )
    );
    RecyclingBin b = new RecyclingBin(
      r.publicCode().trim(),
      r.name().trim(),
      l,
      r.status(),
      r.capacityPercent()
    );
    b.replaceAcceptedItems(resolve(r.acceptedItemCodes()));
    return BinResponse.from(bins.save(b));
  }

  @Transactional
  public BinResponse update(UUID id, SaveBinRequest r) {
    RecyclingBin b = bins
      .findById(id)
      .orElseThrow(() -> new NotFoundException("Recycling bin was not found."));
    if (
      !b.getPublicCode().equalsIgnoreCase(r.publicCode())
    ) throw new BusinessRuleException(
      "The public bin code cannot be changed after creation."
    );
    b.getLocation().update(
      r.locationName().trim(),
      r.address().trim(),
      r.latitude(),
      r.longitude()
    );
    b.update(r.name().trim(), r.status(), r.capacityPercent());
    b.replaceAcceptedItems(resolve(r.acceptedItemCodes()));
    return BinResponse.from(b);
  }

  private Set<AcceptedItemType> resolve(Set<String> cs) {
    Set<String> n = cs
      .stream()
      .map(x -> x.trim().toUpperCase(Locale.ROOT))
      .collect(Collectors.toSet());
    List<AcceptedItemType> found = items.findByCodeIn(n);
    if (found.size() != n.size()) throw new NotFoundException(
      "One or more accepted-item codes do not exist."
    );
    return new LinkedHashSet<>(found);
  }
}
