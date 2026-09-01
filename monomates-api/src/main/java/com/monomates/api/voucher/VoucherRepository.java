package com.monomates.api.voucher;

import jakarta.persistence.LockModeType;
import java.util.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface VoucherRepository extends JpaRepository<Voucher, UUID> {
  Optional<Voucher> findByTitleIgnoreCase(String title);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select v from Voucher v where v.id=:id")
  Optional<Voucher> findByIdForUpdate(@Param("id") UUID id);
}
