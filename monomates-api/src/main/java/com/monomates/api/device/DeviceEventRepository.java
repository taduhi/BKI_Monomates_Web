package com.monomates.api.device;

import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DeviceEventRepository
  extends JpaRepository<DeviceEvent, UUID>
{
  Optional<DeviceEvent> findByDevice_IdAndExternalEventId(
    UUID deviceId,
    String eventId
  );

  boolean existsByDevice_Bin_Id(UUID binId);
}
