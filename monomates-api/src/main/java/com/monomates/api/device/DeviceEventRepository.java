package com.monomates.api.device;

import java.time.Instant;
import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DeviceEventRepository
  extends JpaRepository<DeviceEvent, UUID>
{
  Optional<DeviceEvent> findByDevice_IdAndExternalEventId(
    UUID deviceId,
    String eventId
  );

  boolean existsByDevice_Bin_Id(UUID binId);

  @Query(
    value = "SELECT MAX(received_at) FROM device_events WHERE device_id = :deviceId AND payload_json LIKE '%monomates-bridge%'",
    nativeQuery = true
  )
  Instant findLatestHardwareEventAt(@Param("deviceId") UUID deviceId);
}
