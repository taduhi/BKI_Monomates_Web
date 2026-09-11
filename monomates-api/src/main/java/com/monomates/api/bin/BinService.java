package com.monomates.api.bin;

import com.monomates.api.bin.dto.*;
import com.monomates.api.common.exception.*;
import com.monomates.api.deposit.DepositSessionRepository;
import com.monomates.api.device.Device;
import com.monomates.api.device.DeviceEventRepository;
import com.monomates.api.device.DeviceRepository;
import com.monomates.api.device.DeviceStatus;
import java.util.*;
import java.util.stream.Collectors;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BinService {

  private final RecyclingBinRepository bins;
  private final LocationRepository locations;
  private final AcceptedItemTypeRepository items;
  private final DeviceRepository devices;
  private final DepositSessionRepository sessions;
  private final DeviceEventRepository deviceEvents;
  private final PasswordEncoder passwordEncoder;

  public BinService(
    RecyclingBinRepository b,
    LocationRepository l,
    AcceptedItemTypeRepository i,
    DeviceRepository d,
    DepositSessionRepository s,
    DeviceEventRepository de,
    PasswordEncoder pe
  ) {
    bins = b;
    locations = l;
    items = i;
    devices = d;
    sessions = s;
    deviceEvents = de;
    passwordEncoder = pe;
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
    RecyclingBin saved = bins.save(b);
    // Every bin needs at least one device row for the demo secret-sort
    // controls to work (they piggyback on the real device-event pipeline,
    // which requires a concrete device) and for real hardware to ever be
    // wired up later. The generated secret is only usable here — there is
    // no admin flow yet to reveal or reset it — so this device is only
    // meaningful for the demo controls until that exists.
    devices.save(
      new Device(
        saved,
        "DEV-" + UUID.randomUUID(),
        passwordEncoder.encode(UUID.randomUUID().toString()),
        DeviceStatus.ACTIVE,
        null
      )
    );
    return BinResponse.from(saved);
  }

  @Transactional
  public BinResponse update(UUID id, SaveBinRequest r) {
    RecyclingBin b = bins
      .findById(id)
      .orElseThrow(() -> new NotFoundException("Recycling bin was not found."));
    String newCode = r.publicCode().trim();
    if (
      !b.getPublicCode().equalsIgnoreCase(newCode) &&
      bins.existsByPublicCodeIgnoreCase(newCode)
    ) throw new ConflictException("A bin already uses this public code.");
    b.getLocation().update(
      r.locationName().trim(),
      r.address().trim(),
      r.latitude(),
      r.longitude()
    );
    b.update(newCode, r.name().trim(), r.status(), r.capacityPercent());
    b.replaceAcceptedItems(resolve(r.acceptedItemCodes()));
    return BinResponse.from(b);
  }

  @Transactional
  public void delete(UUID id) {
    RecyclingBin b = bins
      .findById(id)
      .orElseThrow(() -> new NotFoundException("Recycling bin was not found."));
    // Every bin now gets a device automatically on creation (see create()),
    // so merely having a device row can no longer be the deletion guard —
    // that would make every bin permanent from the moment it's created.
    // Real device *event* history (from real hardware or the demo secret
    // controls) is the actual signal that this bin has been used for real.
    if (deviceEvents.existsByDevice_Bin_Id(id)) throw new BusinessRuleException(
      "This bin's device has recorded real events and cannot be deleted. Set it to Offline instead to keep the history intact."
    );
    if (sessions.existsByBin_Id(id)) throw new BusinessRuleException(
      "This bin has deposit history and cannot be deleted. Set it to Offline instead to keep the history intact."
    );
    UUID locationId = b.getLocation().getId();
    devices.deleteByBin_Id(id);
    bins.delete(b);
    bins.flush();
    if (bins.countByLocation_Id(locationId) == 0) locations.deleteById(locationId);
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
