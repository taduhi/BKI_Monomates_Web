package com.monomates.api.security;

import jakarta.servlet.http.*;
import org.springframework.security.oauth2.server.resource.web.BearerTokenResolver;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class CookieBearerTokenResolver implements BearerTokenResolver {

  private final AuthProperties p;

  public CookieBearerTokenResolver(AuthProperties p) {
    this.p = p;
  }

  public String resolve(HttpServletRequest r) {
    String a = r.getHeader("Authorization");
    if (StringUtils.hasText(a) && a.startsWith("Bearer ")) return a.substring(
      7
    );
    Cookie[] cs = r.getCookies();
    if (cs != null) for (Cookie c : cs)
      if (
        p.cookieName().equals(c.getName()) && StringUtils.hasText(c.getValue())
      ) return c.getValue();
    return null;
  }
}
