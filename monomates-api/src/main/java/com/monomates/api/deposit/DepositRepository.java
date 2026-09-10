package com.monomates.api.deposit;

import java.util.*;
import org.springframework.data.jpa.repository.*;

public interface DepositRepository extends JpaRepository<Deposit, UUID> {
  @EntityGraph(
    attributePaths = {
      "session",
      "session.bin",
      "session.bin.location",
      "itemType",
      "deviceEvent",
    }
  )
  Optional<Deposit> findBySession_Id(UUID sessionId);

  @EntityGraph(
    attributePaths = {
      "session",
      "session.bin",
      "session.bin.location",
      "itemType",
      "deviceEvent",
    }
  )
  Optional<Deposit> findByDeviceEvent_Id(UUID eventId);

  @EntityGraph(
    attributePaths = {
      "session",
      "session.bin",
      "session.bin.location",
      "itemType",
    }
  )
  List<Deposit> findBySession_User_IdOrderByCreatedAtDesc(UUID userId);
}
