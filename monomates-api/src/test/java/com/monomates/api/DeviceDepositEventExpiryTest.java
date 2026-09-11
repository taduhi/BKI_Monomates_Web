package com.monomates.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.servlet.http.Cookie;
import java.util.List;
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
 * A device event that arrives after the session's server-side deadline must
 * be refused (M5 acceptance: "missing/expired session"), even though no
 * client ever polled the session to flip it to EXPIRED first.
 */
@SpringBootTest(
  webEnvironment = SpringBootTest.WebEnvironment.MOCK,
  properties = "app.deposit.session-seconds=2"
)
@ActiveProfiles("local")
class DeviceDepositEventExpiryTest {

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
  void cleanUp() {
    if (registeredEmail == null) return;
    List<UUID> eventIds = jdbc.queryForList(
      """
      select d.device_event_id from deposits d
      join deposit_sessions s on d.session_id = s.id
      join app_users u on s.user_id = u.id
      where u.email = ?
      """,
      UUID.class,
      registeredEmail
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
      registeredEmail
    );
    jdbc.update(
      "delete from deposits where session_id in (select id from deposit_sessions where user_id in (select id from app_users where email = ?))",
      registeredEmail
    );
    for (UUID eventId : eventIds) {
      jdbc.update("delete from device_events where id = ?", eventId);
    }
    jdbc.update(
      "delete from deposit_sessions where user_id in (select id from app_users where email = ?)",
      registeredEmail
    );
    jdbc.update("delete from app_users where email = ?", registeredEmail);
  }

  @Test
  void deviceEventAfterServerDeadlineIsRefusedEvenIfNeverPolled() throws Exception {
    String email = "it-device-expiry-" + UUID.randomUUID() + "@monomates.test";
    String registerBody = """
      {"fullName":"Integration Test","email":"%s","password":"TestPassword123!"}
      """.formatted(email);
    MvcResult registerResult = mvc
      .perform(
        post("/api/v1/auth/register")
          .with(csrf())
          .contentType(MediaType.APPLICATION_JSON)
          .content(registerBody)
      )
      .andExpect(status().isOk())
      .andReturn();
    registeredEmail = email;
    Cookie auth = registerResult.getResponse().getCookie("mm_access_token");
    assertThat(auth).isNotNull();

    MvcResult started = mvc
      .perform(post("/api/v1/bins/{code}/sessions", "BIN-HCMUT-001").with(csrf()).cookie(auth))
      .andExpect(status().isOk())
      .andReturn();
    String sessionId = json.readTree(started.getResponse().getContentAsString()).path("sessionId").asText();

    mvc
      .perform(post("/api/v1/sessions/{id}/scan", sessionId).with(csrf()).cookie(auth))
      .andExpect(status().isOk());

    Thread.sleep(2500);

    String eventBody = """
      {
        "deviceCode": "DEV-HCMUT-001",
        "deviceSecret": "demo-device-secret-hcmut",
        "eventId": "evt-%s",
        "sessionId": "%s",
        "irDetected": true,
        "weightChangeGrams": 24.0,
        "itemType": "CLEAR_PET_BOTTLE",
        "classificationConfidence": 0.95
      }
      """.formatted(UUID.randomUUID(), sessionId);

    mvc
      .perform(post("/api/v1/device/events/deposit").contentType(MediaType.APPLICATION_JSON).content(eventBody))
      .andExpect(status().isUnprocessableEntity());
  }
}
