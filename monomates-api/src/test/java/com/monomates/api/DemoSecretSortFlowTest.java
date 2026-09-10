package com.monomates.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;

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
import tools.jackson.databind.ObjectMapper;

/**
 * Verifies the account-gated "secret sort" endpoint that backs the three
 * decorative status-dot controls (accepted / uncertain / rejected): it must
 * exist regardless of Spring profile (unlike {@code LocalDemoController}),
 * but only the account whose email matches
 * {@code app.demo.secret-account-email} may call it. Any other authenticated
 * user must see a plain 404, exactly like requesting a deposit session owned
 * by someone else.
 */
@SpringBootTest(
  webEnvironment = SpringBootTest.WebEnvironment.MOCK,
  properties = "app.demo.secret-account-email=demo-sort-test@monomates.test"
)
@ActiveProfiles("local")
class DemoSecretSortFlowTest {

  private static final String ACTIVE_BIN = "BIN-HCMUT-001";
  private static final String DEMO_EMAIL = "demo-sort-test@monomates.test";

  @Autowired
  private WebApplicationContext context;

  @Autowired
  private ObjectMapper json;

  @Autowired
  private JdbcTemplate jdbc;

  private MockMvc mvc;
  private final java.util.List<String> registeredEmails = new java.util.ArrayList<>();

  @BeforeEach
  void setUpMockMvc() {
    mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
  }

  @AfterEach
  void removeTestUsersAndSessions() {
    for (String email : registeredEmails) {
      java.util.List<UUID> eventIds = jdbc.queryForList(
        """
        select d.device_event_id from deposits d
        join deposit_sessions s on d.session_id = s.id
        join app_users u on s.user_id = u.id
        where u.email = ?
        """,
        UUID.class,
        email
      );
      jdbc.update(
        """
        delete from token_ledger where deposit_id in (
          select d.id from deposits d
          join deposit_sessions s on d.session_id = s.id
          join app_users u on s.user_id = u.id
          where u.email = ?
        )
        """,
        email
      );
      jdbc.update(
        "delete from deposits where session_id in (select id from deposit_sessions where user_id in (select id from app_users where email = ?))",
        email
      );
      for (UUID eventId : eventIds) {
        jdbc.update("delete from device_events where id = ?", eventId);
      }
      jdbc.update(
        "delete from deposit_sessions where user_id in (select id from app_users where email = ?)",
        email
      );
      jdbc.update("delete from app_users where email = ?", email);
    }
    registeredEmails.clear();
  }

  private Cookie registerUser(String email) throws Exception {
    String body = """
      {"fullName":"Integration Test","email":"%s","password":"TestPassword123!"}
      """.formatted(email);
    MvcResult result = mvc
      .perform(
        post("/api/v1/auth/register")
          .with(csrf())
          .contentType(MediaType.APPLICATION_JSON)
          .content(body)
      )
      .andExpect(status().isOk())
      .andReturn();
    registeredEmails.add(email);
    return result.getResponse().getCookie("mm_access_token");
  }

  private MvcResult startSession(Cookie auth) throws Exception {
    return mvc
      .perform(post("/api/v1/bins/{code}/sessions", ACTIVE_BIN).with(csrf()).cookie(auth))
      .andReturn();
  }

  private String field(MvcResult result, String name) throws Exception {
    return json
      .readTree(result.getResponse().getContentAsString())
      .path(name)
      .asText();
  }

  @Test
  void anAccountOtherThanTheFixedDemoAccountCannotUseTheSecretEndpoint() throws Exception {
    Cookie other = registerUser("it-" + UUID.randomUUID() + "@monomates.test");
    MvcResult started = startSession(other);
    assertThat(started.getResponse().getStatus()).isEqualTo(200);
    String sessionId = field(started, "sessionId");

    mvc
      .perform(
        post("/api/v1/demo/secret-sort")
          .with(csrf())
          .cookie(other)
          .contentType(MediaType.APPLICATION_JSON)
          .content("{\"sessionId\":\"%s\",\"outcome\":\"ACCEPTED_PET\"}".formatted(sessionId))
      )
      .andExpect(status().isNotFound());
  }

  @Test
  void theFixedDemoAccountAcceptedPetOutcomeAwardsTwoTokens() throws Exception {
    Cookie demo = registerUser(DEMO_EMAIL);
    MvcResult started = startSession(demo);
    assertThat(started.getResponse().getStatus()).isEqualTo(200);
    String sessionId = field(started, "sessionId");

    mvc
      .perform(
        post("/api/v1/demo/secret-sort")
          .with(csrf())
          .cookie(demo)
          .contentType(MediaType.APPLICATION_JSON)
          .content("{\"sessionId\":\"%s\",\"outcome\":\"ACCEPTED_PET\"}".formatted(sessionId))
      )
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.tokensAwarded").value(2));

    mvc
      .perform(get("/api/v1/users/me/token-balance").cookie(demo))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.balance").value(2));
  }

  @Test
  void theFixedDemoAccountValidUncertainOutcomeAwardsOneToken() throws Exception {
    Cookie demo = registerUser(DEMO_EMAIL);
    MvcResult started = startSession(demo);
    assertThat(started.getResponse().getStatus()).isEqualTo(200);
    String sessionId = field(started, "sessionId");

    mvc
      .perform(
        post("/api/v1/demo/secret-sort")
          .with(csrf())
          .cookie(demo)
          .contentType(MediaType.APPLICATION_JSON)
          .content("{\"sessionId\":\"%s\",\"outcome\":\"VALID_UNCERTAIN\"}".formatted(sessionId))
      )
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.tokensAwarded").value(1));

    mvc
      .perform(get("/api/v1/users/me/token-balance").cookie(demo))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.balance").value(1));
  }

  @Test
  void theFixedDemoAccountRejectedOutcomeAwardsNoToken() throws Exception {
    Cookie demo = registerUser(DEMO_EMAIL);
    MvcResult started = startSession(demo);
    assertThat(started.getResponse().getStatus()).isEqualTo(200);
    String sessionId = field(started, "sessionId");

    mvc
      .perform(
        post("/api/v1/demo/secret-sort")
          .with(csrf())
          .cookie(demo)
          .contentType(MediaType.APPLICATION_JSON)
          .content("{\"sessionId\":\"%s\",\"outcome\":\"REJECTED\"}".formatted(sessionId))
      )
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.tokensAwarded").value(0));

    mvc
      .perform(get("/api/v1/users/me/token-balance").cookie(demo))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.balance").value(0));
  }

  @Test
  void theFixedDemoAccountCanRestartTheSameBinOnTheSameDayResettingItsOwnHistory()
    throws Exception {
    Cookie demo = registerUser(DEMO_EMAIL);
    MvcResult first = startSession(demo);
    assertThat(first.getResponse().getStatus()).isEqualTo(200);
    String firstSessionId = field(first, "sessionId");

    mvc
      .perform(
        post("/api/v1/demo/secret-sort")
          .with(csrf())
          .cookie(demo)
          .contentType(MediaType.APPLICATION_JSON)
          .content("{\"sessionId\":\"%s\",\"outcome\":\"ACCEPTED_PET\"}".formatted(firstSessionId))
      )
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.tokensAwarded").value(2));

    // A normal account would be rejected with 409 here (one session per
    // bin/day); the fixed demo account instead resets its own record for
    // this bin/day and is allowed to start again.
    MvcResult second = startSession(demo);
    assertThat(second.getResponse().getStatus()).isEqualTo(200);
    assertThat(field(second, "sessionId")).isNotEqualTo(firstSessionId);

    // The first run's reward is gone, not just superseded — the demo
    // account's balance reflects only the fresh run going forward.
    mvc
      .perform(get("/api/v1/users/me/token-balance").cookie(demo))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.balance").value(0));
  }
}
