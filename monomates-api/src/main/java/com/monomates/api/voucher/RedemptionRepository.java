package com.monomates.api.voucher;

import java.util.*;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RedemptionRepository extends JpaRepository<Redemption, UUID> {
  Optional<Redemption> findByRedemptionCode(String redemptionCode);

  @EntityGraph(attributePaths = { "voucher" })
  List<Redemption> findByUser_IdOrderByCreatedAtDesc(UUID userId);

  long countByUser_IdAndVoucher_Id(UUID userId, UUID voucherId);
}
