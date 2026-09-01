package com.monomates.api.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.rate-limit")
public record RateLimitProperties(
  int authRequestsPerMinute,
  int apiRequestsPerMinute
) {}
