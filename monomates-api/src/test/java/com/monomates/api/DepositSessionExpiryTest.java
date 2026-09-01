package com.monomates.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.servlet.http.Cookie;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import tools.jackson.databind.ObjectMapper;

/**
 * Runs in its own Spring context with a short session window so expiry can
 * be observed without a real 60-second wait. Confirms expiry is decided by
 * the backend clock, not a client-side timer (M3 acceptance criterion).
 */
@SpringBootTest(
  webEnvironment = SpringBootTest.WebEnvironment.MOCK,
  properties = "app.deposit.session-seconds=2"
)
@ActiveProfiles("local")
class DepositSessionExpiryTest {

  @Autowired
  private WebApplicationContext context;

  @Autowired
  private ObjectMapper json;

  @Autowired
  private JdbcTemplate jdbc;

  private MockMvc mvc;
  private String registeredEmail;

  @BeforeEach
  void setUpMockMvc() {
    mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
  }

  @AfterEach
  void removeTestUserAndSessions() {
    if (registeredEmail == null) return;
    jdbc.update(
      "delete from deposit_sessions where user_id in (select id from app_users where email = ?)",
      registeredEmail
    );
    jdbc.update("delete from app_users where email = ?", registeredEmail);
  }

  @Test
  void activeSessionTransitionsToExpiredOncePastItsServerDeadline() throws Exception {
    String email = "it-" + UUID.randomUUID() + "@monomates.test";
    String body = """
      {"fullName":"Integration Test","email":"%s","password":"TestPassword123!"}
      """.formatted(email);
    MvcResult registerResult = mvc
      .perform(
        post("/api/v1/auth/register")
          .with(csrf())
          .contentType(MediaType.APPLICATION_JSON)
          .content(body)
      )
      .andExpect(status().isOk())
      .andReturn();
    registeredEmail = email;
    Cookie auth = registerResult.getResponse().getCookie("mm_access_token");
    assertThat(auth).isNotNull();

    MvcResult started = mvc
      .perform(post("/api/v1/bins/{code}/sessions", "BIN-HCMUT-001").with(csrf()).cookie(auth))
      .andReturn();
    assertThat(started.getResponse().getStatus()).isEqualTo(200);
    String sessionId = json
      .readTree(started.getResponse().getContentAsString())
      .path("sessionId")
      .asText();

    Thread.sleep(2500);

    MvcResult refreshed = mvc
      .perform(get("/api/v1/sessions/{id}", sessionId).cookie(auth))
      .andExpect(status().isOk())
      .andReturn();
    String finalStatus = json
      .readTree(refreshed.getResponse().getContentAsString())
      .path("status")
      .asText();
    assertThat(finalStatus).isEqualTo("EXPIRED");
  }
}
