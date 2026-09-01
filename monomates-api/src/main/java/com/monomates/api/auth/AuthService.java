package com.monomates.api.auth;

import com.monomates.api.auth.dto.*;
import com.monomates.api.common.exception.*;
import com.monomates.api.security.JwtService;
import com.monomates.api.user.*;
import com.monomates.api.user.dto.UserResponse;
import java.util.Locale;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

  private final UserRepository users;
  private final PasswordEncoder passwords;
  private final JwtService jwt;

  public AuthService(UserRepository u, PasswordEncoder p, JwtService j) {
    users = u;
    passwords = p;
    jwt = j;
  }

  @Transactional
  public LoginResult register(RegisterRequest r) {
    String e = norm(r.email());
    if (users.existsByEmailIgnoreCase(e)) throw new ConflictException(
      "An account already exists for this email address."
    );
    UserAccount u = users.save(
      new UserAccount(
        e,
        passwords.encode(r.password()),
        r.fullName().trim(),
        UserRole.USER,
        UserStatus.ACTIVE
      )
    );
    return result(u);
  }

  @Transactional(readOnly = true)
  public LoginResult login(LoginRequest r) {
    UserAccount u = users
      .findByEmailIgnoreCase(norm(r.email()))
      .orElseThrow(() ->
        new BusinessRuleException("Email or password is incorrect.")
      );
    if (
      u.getStatus() != UserStatus.ACTIVE ||
      !passwords.matches(r.password(), u.getPasswordHash())
    ) throw new BusinessRuleException("Email or password is incorrect.");
    return result(u);
  }

  private LoginResult result(UserAccount u) {
    JwtService.TokenResult t = jwt.createAccessToken(u);
    return new LoginResult(
      t.token(),
      new AuthResponse(UserResponse.from(u), t.expiresAt())
    );
  }

  private String norm(String e) {
    return e.trim().toLowerCase(Locale.ROOT);
  }

  public record LoginResult(String token, AuthResponse response) {}
}
