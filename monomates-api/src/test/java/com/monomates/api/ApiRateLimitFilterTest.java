package com.monomates.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.monomates.api.security.ApiRateLimitFilter;
import com.monomates.api.security.RateLimitProperties;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import tools.jackson.databind.json.JsonMapper;

class ApiRateLimitFilterTest {

  @Test
  void authenticationAttemptsAreRateLimitedPerRemoteAddress() throws Exception {
    ApiRateLimitFilter filter = new ApiRateLimitFilter(
      new RateLimitProperties(2, 10),
      JsonMapper.builder().build()
    );

    assertThat(invoke(filter, "POST", "/api/v1/auth/login")).isEqualTo(204);
    assertThat(invoke(filter, "POST", "/api/v1/auth/login")).isEqualTo(204);
    MockHttpServletResponse rejected = response(filter, "POST", "/api/v1/auth/login");

    assertThat(rejected.getStatus()).isEqualTo(429);
    assertThat(rejected.getHeader("Retry-After")).isNotBlank();
    assertThat(rejected.getContentAsString()).contains("Too many requests");
  }

  @Test
  void deviceRoutesAreExcludedFromTheWebRateLimiter() throws Exception {
    ApiRateLimitFilter filter = new ApiRateLimitFilter(
      new RateLimitProperties(1, 1),
      JsonMapper.builder().build()
    );

    assertThat(invoke(filter, "POST", "/api/v1/device/events/deposit")).isEqualTo(204);
    assertThat(invoke(filter, "POST", "/api/v1/device/events/deposit")).isEqualTo(204);
  }

  private int invoke(ApiRateLimitFilter filter, String method, String path) throws Exception {
    return response(filter, method, path).getStatus();
  }

  private MockHttpServletResponse response(
    ApiRateLimitFilter filter,
    String method,
    String path
  ) throws Exception {
    MockHttpServletRequest request = new MockHttpServletRequest(method, path);
    request.setRemoteAddr("203.0.113.10");
    MockHttpServletResponse response = new MockHttpServletResponse();
    filter.doFilter(
      request,
      response,
      (ignoredRequest, rawResponse) ->
        ((HttpServletResponse) rawResponse).setStatus(204)
    );
    return response;
  }
}
