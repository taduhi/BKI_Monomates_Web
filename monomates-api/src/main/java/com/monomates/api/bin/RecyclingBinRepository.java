package com.monomates.api.bin;

import java.util.*;
import org.springframework.data.jpa.repository.*;

public interface RecyclingBinRepository
  extends JpaRepository<RecyclingBin, UUID>
{
  @EntityGraph(attributePaths = { "location", "acceptedItems" })
  Optional<RecyclingBin> findByPublicCodeIgnoreCase(String code);

  @Override
  @EntityGraph(attributePaths = { "location", "acceptedItems" })
  List<RecyclingBin> findAll();

  boolean existsByPublicCodeIgnoreCase(String code);
}
