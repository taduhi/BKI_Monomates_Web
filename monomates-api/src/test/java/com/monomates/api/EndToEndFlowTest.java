package com.monomates.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
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
 * M9: end-to-end walk of the whole Prototype v0 golden path — auth, bin
 * discovery, QR session, simulated deposit, reward, activity, and voucher
 * redemption — through real HTTP-shaped requests, the real security filter
 * chain, and real PostgreSQL. Encodes the final acceptance checklist from
 * implementation_plan.md M9 as executable assertions.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@ActiveProfiles("local")
class EndToEndFlowTest {

  @Autowired
  private WebApplicationContext context;

  @Autowired
  private ObjectMapper json;

  @Autowired
  private JdbcTemplate jdbc;

  private MockMvc mvc;
  private String registeredEmail;
  private UUID createdVoucherId;

  @BeforeEach
  void setUpMockMvc() {
    mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
  }

  @AfterEach
  void cleanUp() {
    if (registeredEmail != null) {
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
        "delete from token_ledger where user_id in (select id from app_users where email = ?)",
        registeredEmail
      );
      jdbc.update(
        "delete from redemptions where user_id in (select id from app_users where email = ?)",
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
    if (createdVoucherId != null) {
      jdbc.update("delete from token_ledger where redemption_id in (select id from redemptions where voucher_id = ?)", createdVoucherId);
      jdbc.update("delete from redemptions where voucher_id = ?", createdVoucherId);
      jdbc.update("delete from vouchers where id = ?", createdVoucherId);
    }
  }

  private String field(MvcResult result, String name) throws Exception {
    return json.readTree(result.getResponse().getContentAsString()).path(name).asText();
  }

  private int intField(MvcResult result, String name) throws Exception {
    return json.readTree(result.getResponse().getContentAsString()).path(name).asInt();
  }

  @Test
  void authToBinToScanToSimulateToRewardToActivityToRedeem() throws Exception {
    // 1. Register (auth).
    String email = "it-e2e-" + UUID.randomUUID() + "@monomates.test";
    registeredEmail = email;
    MvcResult register = mvc
      .perform(
        post("/api/v1/auth/register")
          .with(csrf())
          .contentType(MediaType.APPLICATION_JSON)
          .content("{\"fullName\":\"E2E Test\",\"email\":\"%s\",\"password\":\"TestPassword123!\"}".formatted(email))
      )
      .andExpect(status().isOk())
      .andReturn();
    Cookie auth = register.getResponse().getCookie("mm_access_token");
    assertThat(auth).isNotNull();

    // 2. Discover bins.
    MvcResult bins = mvc.perform(get("/api/v1/bins")).andExpect(status().isOk()).andReturn();
    assertThat(bins.getResponse().getContentAsString()).contains("BIN-HCMUT-001");

    // 3. Scan QR -> start a deposit session. Session creation alone must
    // never award a token (core principle: QR scan alone = 0 PT).
    MvcResult session = mvc
      .perform(post("/api/v1/bins/{code}/sessions", "BIN-HCMUT-001").with(csrf()).cookie(auth))
      .andExpect(status().isOk())
      .andReturn();
    String sessionId = field(session, "sessionId");
    assertThat(field(session, "status")).isEqualTo("ACTIVE");

    MvcResult balanceAfterScan = mvc
      .perform(get("/api/v1/users/me/token-balance").cookie(auth))
      .andExpect(status().isOk())
      .andReturn();
    assertThat(intField(balanceAfterScan, "balance")).isEqualTo(0);

    // 4. Simulate a hardware deposit event (accepted clear PET -> +2 PT).
    MvcResult simulate = mvc
      .perform(
        post("/api/v1/testing/simulate-deposit")
          .with(csrf())
          .cookie(auth)
          .contentType(MediaType.APPLICATION_JSON)
          .content("{\"sessionId\":\"%s\",\"outcome\":\"ACCEPTED_PET\"}".formatted(sessionId))
      )
      .andExpect(status().isOk())
      .andReturn();
    assertThat(field(simulate, "status")).isEqualTo("ACCEPTED");
    assertThat(intField(simulate, "tokensAwarded")).isEqualTo(2);

    // 5. Session must now read back as COMPLETED, decided by the backend.
    mvc
      .perform(get("/api/v1/sessions/{id}", sessionId).cookie(auth))
      .andExpect(status().isOk())
      .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.status").value("COMPLETED"))
      .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.tokensAwarded").value(2));

    // 6. Reward reflected in balance and grouped ledger/activity.
    MvcResult balanceAfterReward = mvc
      .perform(get("/api/v1/users/me/token-balance").cookie(auth))
      .andExpect(status().isOk())
      .andReturn();
    assertThat(intField(balanceAfterReward, "balance")).isEqualTo(2);

    MvcResult deposits = mvc
      .perform(get("/api/v1/users/me/deposits").cookie(auth))
      .andExpect(status().isOk())
      .andReturn();
    assertThat(deposits.getResponse().getContentAsString()).contains("\"tokensAwarded\":2");

    MvcResult ledger = mvc
      .perform(get("/api/v1/users/me/token-ledger").cookie(auth))
      .andExpect(status().isOk())
      .andReturn();
    assertThat(ledger.getResponse().getContentAsString())
      .contains(email, "DEPOSIT_BASE", "PET_BONUS");

    // 7. Admin creates a voucher this user can just afford.
    MvcResult adminLogin = mvc
      .perform(
        post("/api/v1/auth/login")
          .with(csrf())
          .contentType(MediaType.APPLICATION_JSON)
          .content("{\"email\":\"admin@monomates.local\",\"password\":\"Admin123!\"}")
      )
      .andExpect(status().isOk())
      .andReturn();
    Cookie adminAuth = adminLogin.getResponse().getCookie("mm_access_token");
    MvcResult voucherCreated = mvc
      .perform(
        post("/api/v1/admin/vouchers")
          .with(csrf())
          .cookie(adminAuth)
          .contentType(MediaType.APPLICATION_JSON)
          .content(
            "{\"partnerName\":\"E2E Partner\",\"title\":\"E2E Voucher\",\"description\":\"e2e\",\"tokenCost\":2,\"inventory\":1,\"status\":\"ACTIVE\"}"
          )
      )
      .andExpect(status().isOk())
      .andReturn();
    createdVoucherId = UUID.fromString(field(voucherCreated, "id"));

    // 8. Redeem it: atomic, no double-spend, balance goes to exactly 0.
    MvcResult redeem = mvc
      .perform(post("/api/v1/vouchers/{id}/redeem", createdVoucherId).with(csrf()).cookie(auth))
      .andExpect(status().isOk())
      .andReturn();
    assertThat(field(redeem, "redemptionCode")).startsWith("MM-");
    assertThat(intField(redeem, "remainingBalance")).isEqualTo(0);

    MvcResult finalBalance = mvc
      .perform(get("/api/v1/users/me/token-balance").cookie(auth))
      .andExpect(status().isOk())
      .andReturn();
    assertThat(intField(finalBalance, "balance")).isEqualTo(0);
  }

  @Test
  void anUnlinkedOrInvalidDepositEventNeverAwardsAToken() throws Exception {
    String email = "it-e2e-unlinked-" + UUID.randomUUID() + "@monomates.test";
    registeredEmail = email;
    MvcResult register = mvc
      .perform(
        post("/api/v1/auth/register")
          .with(csrf())
          .contentType(MediaType.APPLICATION_JSON)
          .content("{\"fullName\":\"E2E Test\",\"email\":\"%s\",\"password\":\"TestPassword123!\"}".formatted(email))
      )
      .andExpect(status().isOk())
      .andReturn();
    Cookie auth = register.getResponse().getCookie("mm_access_token");

    MvcResult session = mvc
      .perform(post("/api/v1/bins/{code}/sessions", "BIN-HCMUT-001").with(csrf()).cookie(auth))
      .andExpect(status().isOk())
      .andReturn();
    String sessionId = field(session, "sessionId");

    // A rejected physical event (no IR detection) must award 0 PT.
    MvcResult simulate = mvc
      .perform(
        post("/api/v1/testing/simulate-deposit")
          .with(csrf())
          .cookie(auth)
          .contentType(MediaType.APPLICATION_JSON)
          .content("{\"sessionId\":\"%s\",\"outcome\":\"REJECTED\"}".formatted(sessionId))
      )
      .andExpect(status().isOk())
      .andReturn();
    assertThat(intField(simulate, "tokensAwarded")).isEqualTo(0);

    MvcResult balance = mvc
      .perform(get("/api/v1/users/me/token-balance").cookie(auth))
      .andExpect(status().isOk())
      .andReturn();
    assertThat(intField(balance, "balance")).isEqualTo(0);
  }
}
