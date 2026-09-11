package com.monomates.api.device;

import java.util.*;
import org.springframework.data.jpa.repository.*;

public interface DeviceRepository extends JpaRepository<Device, UUID> {
  @EntityGraph(attributePaths = { "bin", "bin.location", "bin.acceptedItems" })
  Optional<Device> findByDeviceCodeIgnoreCase(String code);

  @EntityGraph(attributePaths = { "bin", "bin.location", "bin.acceptedItems" })
  Optional<Device> findFirstByBin_Id(UUID binId);

  boolean existsByBin_Id(UUID binId);

  void deleteByBin_Id(UUID binId);
}
