package com.monomates.api.reward;

import java.util.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface TokenLedgerRepository
  extends JpaRepository<TokenLedgerEntry, UUID>
{
  @Query(
    "select coalesce(sum(t.amount),0) from TokenLedgerEntry t where t.user.id=:userId"
  )
  long balance(@Param("userId") UUID userId);

  @Query(
    "select coalesce(sum(t.amount),0) from TokenLedgerEntry t where t.deposit.id=:depositId"
  )
  long tokensForDeposit(@Param("depositId") UUID depositId);

  boolean existsByUser_IdAndTransactionTypeAndDescription(
    UUID userId,
    TokenTransactionType transactionType,
    String description
  );

  @EntityGraph(
    attributePaths = {
      "deposit",
      "deposit.session",
      "deposit.session.bin",
      "redemption",
      "redemption.voucher",
    }
  )
  List<TokenLedgerEntry> findByUser_IdOrderByCreatedAtDesc(UUID userId);

  @Override
  @EntityGraph(
    attributePaths = {
      "user",
      "deposit",
      "deposit.session",
      "deposit.session.bin",
      "redemption",
      "redemption.voucher",
    }
  )
  List<TokenLedgerEntry> findAll();
}
