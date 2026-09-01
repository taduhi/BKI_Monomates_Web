package com.monomates.api.bin.dto;

import com.monomates.api.bin.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;

public record BinResponse(
  UUID id,
  String publicCode,
  String name,
  String status,
  int capacityPercent,
  Instant lastSeenAt,
  Instant updatedAt,
  LocationResponse location,
  List<AcceptedItemResponse> acceptedItems
) {
  public static BinResponse from(RecyclingBin b) {
    return new BinResponse(
      b.getId(),
      b.getPublicCode(),
      b.getName(),
      b.getStatus().name(),
      b.getCapacityPercent(),
      b.getLastSeenAt(),
      b.getUpdatedAt(),
      new LocationResponse(
        b.getLocation().getId(),
        b.getLocation().getName(),
        b.getLocation().getAddress(),
        b.getLocation().getLatitude(),
        b.getLocation().getLongitude()
      ),
      b.getAcceptedItems().stream().map(AcceptedItemResponse::from).toList()
    );
  }

  public record LocationResponse(
    UUID id,
    String name,
    String address,
    BigDecimal latitude,
    BigDecimal longitude
  ) {}

  public record AcceptedItemResponse(
    String code,
    String name,
    String description,
    int baseTokens,
    int bonusTokens
  ) {
    static AcceptedItemResponse from(AcceptedItemType i) {
      return new AcceptedItemResponse(
        i.getCode(),
        i.getName(),
        i.getDescription(),
        i.getBaseTokens(),
        i.getBonusTokens()
      );
    }
  }
}
