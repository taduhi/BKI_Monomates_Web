package com.monomates.api.bin;

import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AcceptedItemTypeRepository
  extends JpaRepository<AcceptedItemType, UUID>
{
  Optional<AcceptedItemType> findByCodeIgnoreCase(String code);
  List<AcceptedItemType> findByCodeIn(Collection<String> codes);
}
