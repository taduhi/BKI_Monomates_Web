package com.monomates.api.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.auth")
public record AuthProperties(
  String issuer,
  String jwtSecret,
  long accessTokenMinutes,
  String cookieName,
  boolean cookieSecure,
  String cookieSameSite
) {}
