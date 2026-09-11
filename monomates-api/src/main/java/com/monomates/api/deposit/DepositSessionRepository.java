package com.monomates.api.deposit;

import jakarta.persistence.LockModeType;
import java.time.LocalDate;
import java.util.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface DepositSessionRepository
  extends JpaRepository<DepositSession, UUID>
{
  long countByUser_IdAndSessionDate(UUID userId, LocalDate date);

  boolean existsByBin_Id(UUID binId);

  // Used only by DemoDataInitializer to avoid re-inserting the same
  // synthetic historical row on every local-profile app restart — unrelated
  // to the (removed) once-a-day-per-bin business rule.
  boolean existsByUser_IdAndBin_IdAndSessionDate(
    UUID userId,
    UUID binId,
    LocalDate date
  );

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  Optional<DepositSession> findByBin_IdAndStatus(
    UUID binId,
    SessionStatus status
  );

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select s from DepositSession s where s.id = :id")
  Optional<DepositSession> findByIdForUpdate(@Param("id") UUID id);

  @Override
  @EntityGraph(
    attributePaths = { "user", "bin", "bin.location", "bin.acceptedItems" }
  )
  Optional<DepositSession> findById(UUID id);
}
