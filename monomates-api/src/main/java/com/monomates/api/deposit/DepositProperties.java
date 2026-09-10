package com.monomates.api.deposit;

import java.math.BigDecimal;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.deposit")
public record DepositProperties(
  long sessionSeconds,
  BigDecimal minimumWeightGrams,
  BigDecimal minimumClassificationConfidence,
  int dailyScanLimit
) {}
