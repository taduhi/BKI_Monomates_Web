package com.monomates.api.security;

import com.monomates.api.deposit.DepositProperties;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.*;
import org.springframework.core.env.Environment;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;

@Configuration
@EnableConfigurationProperties({
  AuthProperties.class,
  DepositProperties.class,
  RateLimitProperties.class,
})
public class JwtConfiguration {

  private static final String PUBLISHED_DEV_SECRET =
    "local-development-secret-change-me-1234567890";

  @Bean
  SecretKey jwtSecretKey(AuthProperties p, Environment env) {
    byte[] b = p.jwtSecret().getBytes(StandardCharsets.UTF_8);
    if (b.length < 32) throw new IllegalStateException(
      "JWT_SECRET must contain at least 32 bytes."
    );
    boolean prod = Arrays.asList(env.getActiveProfiles()).contains("prod");
    if (prod && PUBLISHED_DEV_SECRET.equals(p.jwtSecret())) throw new IllegalStateException(
      "JWT_SECRET must be overridden in the prod profile; refusing to start with the published development default."
    );
    if (prod && !p.cookieSecure()) throw new IllegalStateException(
      "COOKIE_SECURE must be true in the prod profile."
    );
    return new SecretKeySpec(b, "HmacSHA256");
  }

  @Bean
  JwtEncoder jwtEncoder(SecretKey k) {
    return NimbusJwtEncoder.withSecretKey(k)
      .algorithm(MacAlgorithm.HS256)
      .build();
  }

  @Bean
  JwtDecoder jwtDecoder(SecretKey k, AuthProperties p) {
    NimbusJwtDecoder d = NimbusJwtDecoder.withSecretKey(k)
      .macAlgorithm(MacAlgorithm.HS256)
      .build();
    d.setJwtValidator(JwtValidators.createDefaultWithIssuer(p.issuer()));
    return d;
  }
}
