package com.monomates.api.security;

import com.monomates.api.common.exception.UnauthorizedException;
import com.monomates.api.user.*;
import java.util.UUID;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;

@Service
public class CurrentUserService {

  private final UserRepository users;

  public CurrentUserService(UserRepository u) {
    users = u;
  }

  public UserAccount require(Jwt j) {
    UserAccount u;
    try {
      u = users
        .findById(UUID.fromString(j.getSubject()))
        .orElseThrow(() ->
          new UnauthorizedException("Authenticated user was not found.")
        );
    } catch (IllegalArgumentException e) {
      throw new UnauthorizedException("Authenticated user was not found.");
    }
    if (u.getStatus() != UserStatus.ACTIVE) throw new UnauthorizedException(
      "This account is no longer active."
    );
    return u;
  }
}
