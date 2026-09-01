package com.monomates.api.security;

import com.monomates.api.common.exception.ApiErrorResponse;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.ObjectMapper;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 20)
public class ApiRateLimitFilter extends OncePerRequestFilter {

  private static final long WINDOW_MILLIS = 60_000L;
  private final RateLimitProperties properties;
  private final ObjectMapper json;
  private final ConcurrentHashMap<String, Window> windows =
    new ConcurrentHashMap<>();
  private final AtomicLong requestCounter = new AtomicLong();

  public ApiRateLimitFilter(RateLimitProperties properties, ObjectMapper json) {
    this.properties = properties;
    this.json = json;
  }

  @Override
  protected boolean shouldNotFilter(HttpServletRequest request) {
    String path = request.getRequestURI();
    return (
      "OPTIONS".equalsIgnoreCase(request.getMethod()) ||
      !path.startsWith("/api/v1/") ||
      path.startsWith("/api/v1/device/") ||
      path.equals("/api/v1/status")
    );
  }

  @Override
  protected void doFilterInternal(
    HttpServletRequest request,
    HttpServletResponse response,
    FilterChain chain
  ) throws ServletException, IOException {
    long now = System.currentTimeMillis();
    boolean auth = isAuthenticationAttempt(request);
    int limit = auth
      ? properties.authRequestsPerMinute()
      : properties.apiRequestsPerMinute();
    if (limit <= 0) {
      chain.doFilter(request, response);
      return;
    }

    String key = (auth ? "auth:" : "api:") + request.getRemoteAddr();
    AtomicBoolean allowed = new AtomicBoolean();
    Window current = windows.compute(key, (ignored, previous) -> {
      Window next = previous == null || now - previous.startedAt() >= WINDOW_MILLIS
        ? new Window(now, 1)
        : new Window(previous.startedAt(), previous.count() + 1);
      allowed.set(next.count() <= limit);
      return next;
    });

    if (requestCounter.incrementAndGet() % 1_000 == 0) {
      windows.entrySet().removeIf(
        entry -> now - entry.getValue().startedAt() >= WINDOW_MILLIS
      );
    }

    response.setHeader("X-RateLimit-Limit", Integer.toString(limit));
    if (!allowed.get()) {
      long retrySeconds = Math.max(
        1,
        (WINDOW_MILLIS - (now - current.startedAt()) + 999) / 1_000
      );
      response.setHeader("Retry-After", Long.toString(retrySeconds));
      response.setStatus(429);
      response.setContentType(MediaType.APPLICATION_JSON_VALUE);
      json.writeValue(
        response.getOutputStream(),
        new ApiErrorResponse(
          Instant.now(),
          429,
          "Too Many Requests",
          "Too many requests. Please wait before trying again.",
          request.getRequestURI(),
          null
        )
      );
      return;
    }

    chain.doFilter(request, response);
  }

  private boolean isAuthenticationAttempt(HttpServletRequest request) {
    if (!"POST".equalsIgnoreCase(request.getMethod())) return false;
    String path = request.getRequestURI();
    return path.equals("/api/v1/auth/login") ||
      path.equals("/api/v1/auth/register");
  }

  private record Window(long startedAt, int count) {}
}
