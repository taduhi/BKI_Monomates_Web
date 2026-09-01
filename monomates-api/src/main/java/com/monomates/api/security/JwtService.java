package com.monomates.api.security;

import com.monomates.api.user.UserAccount;
import java.time.Instant;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.stereotype.Service;

@Service
public class JwtService {

  private final JwtEncoder encoder;
  private final AuthProperties p;

  public JwtService(JwtEncoder e, AuthProperties p) {
    encoder = e;
    this.p = p;
  }

  public TokenResult createAccessToken(UserAccount u) {
    Instant i = Instant.now(),
      x = i.plusSeconds(p.accessTokenMinutes() * 60);
    JwtClaimsSet c = JwtClaimsSet.builder()
      .issuer(p.issuer())
      .issuedAt(i)
      .expiresAt(x)
      .subject(u.getId().toString())
      .claim("email", u.getEmail())
      .claim("role", u.getRole().name())
      .build();
    JwsHeader h = JwsHeader.with(MacAlgorithm.HS256).build();
    return new TokenResult(
      encoder.encode(JwtEncoderParameters.from(h, c)).getTokenValue(),
      x
    );
  }

  public record TokenResult(String token, Instant expiresAt) {}
}
