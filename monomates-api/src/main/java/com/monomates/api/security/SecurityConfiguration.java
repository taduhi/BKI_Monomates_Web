package com.monomates.api.security;

import java.time.Instant;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import com.monomates.api.common.exception.ApiErrorResponse;

import jakarta.servlet.http.HttpServletResponse;
import tools.jackson.databind.ObjectMapper;

@Configuration
@EnableMethodSecurity
public class SecurityConfiguration {

  @Bean
  PasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder(12);
  }

  @Bean
  SecurityFilterChain chain(
    HttpSecurity h,
    CookieBearerTokenResolver r,
    ObjectMapper m,
    CorsConfigurationSource corsConfigurationSource
  ) throws Exception {
    CookieCsrfTokenRepository csrfRepository =
      CookieCsrfTokenRepository.withHttpOnlyFalse();
    CsrfTokenRequestAttributeHandler csrfHandler =
      new CsrfTokenRequestAttributeHandler();

    h.cors(cors -> cors.configurationSource(corsConfigurationSource))
      .csrf(x ->
        x
          .csrfTokenRepository(csrfRepository)
          .csrfTokenRequestHandler(csrfHandler)
          .ignoringRequestMatchers("/api/v1/device/**")
      )
      .sessionManagement(x ->
        x.sessionCreationPolicy(SessionCreationPolicy.STATELESS)
      )
      .authorizeHttpRequests(a ->
        a
          .requestMatchers(HttpMethod.OPTIONS, "/**")
          .permitAll()
          .requestMatchers(
            "/actuator/health",
            "/api/v1/status",
            "/api/v1/auth/csrf",
            "/api/v1/auth/register",
            "/api/v1/auth/login",
            "/api/v1/auth/logout"
          )
          .permitAll()
          .requestMatchers(
            HttpMethod.GET,
            "/api/v1/bins/**",
            "/api/v1/vouchers"
          )
          .permitAll()
          .requestMatchers("/api/v1/device/**")
          .permitAll()
          .requestMatchers("/api/v1/admin/**")
          .hasRole("ADMIN")
          .anyRequest()
          .authenticated()
      )
      .oauth2ResourceServer(o ->
        o
          .bearerTokenResolver(r)
          .jwt(j -> j.jwtAuthenticationConverter(jwtConverter()))
          .authenticationEntryPoint((q, s, e) ->
            write(
              m,
              s,
              401,
              "Unauthorized",
              "Authentication is required.",
              q.getRequestURI()
            )
          )
          .accessDeniedHandler((q, s, e) ->
            write(
              m,
              s,
              403,
              "Forbidden",
              "You do not have permission to perform this action.",
              q.getRequestURI()
            )
          )
      );
    return h.build();
  }

  @Bean
  JwtAuthenticationConverter jwtConverter() {
    JwtGrantedAuthoritiesConverter g = new JwtGrantedAuthoritiesConverter();
    g.setAuthoritiesClaimName("role");
    g.setAuthorityPrefix("ROLE_");
    JwtAuthenticationConverter c = new JwtAuthenticationConverter();
    c.setJwtGrantedAuthoritiesConverter(g);
    c.setPrincipalClaimName("sub");
    return c;
  }

  @Bean
  CorsConfigurationSource corsConfigurationSource(
    @Value("${app.frontend-origin}") String origin
  ) {
    CorsConfiguration c = new CorsConfiguration();
    c.setAllowedOrigins(List.of(origin));
    c.setAllowedMethods(
      List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
    );
    c.setAllowedHeaders(
      List.of("Authorization", "Content-Type", "Accept", "X-XSRF-TOKEN")
    );
    c.setAllowCredentials(true);
    c.setMaxAge(3600L);
    UrlBasedCorsConfigurationSource s = new UrlBasedCorsConfigurationSource();
    s.registerCorsConfiguration("/api/**", c);
    return s;
  }

  private static void write(
    ObjectMapper m,
    HttpServletResponse s,
    int code,
    String error,
    String msg,
    String path
  ) throws java.io.IOException {
    s.setStatus(code);
    s.setContentType(MediaType.APPLICATION_JSON_VALUE);
    m.writeValue(
      s.getOutputStream(),
      new ApiErrorResponse(Instant.now(), code, error, msg, path, null)
    );
  }
}
