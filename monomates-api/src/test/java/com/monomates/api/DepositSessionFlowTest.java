package com.monomates.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import jakarta.servlet.http.Cookie;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
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
 * Exercises the M3 deposit-session flow end to end through real HTTP-shaped
 * MockMvc requests, the real Spring Security/CSRF filter chain and the real
 * PostgreSQL schema (Flyway V1+V2). Converts the manual smoke matrix run in
 * CX-005 into an automated, repeatable regression test.
 *
 * Every test method that occupies {@link #ACTIVE_BIN} cancels its own
 * session before returning so the tests remain independent of execution
 * order and safely re-runnable against the same database.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@ActiveProfiles("local")
class DepositSessionFlowTest {

  private static final String ACTIVE_BIN = "BIN-HCMUT-001";
  private static final String SECOND_ACTIVE_BIN = "BIN-YOUTH-001";
  private static final String MAINTENANCE_BIN = "BIN-D10-001";

  @Autowired
  private WebApplicationContext context;

  @Autowired
  private ObjectMapper json;

  @Autowired
  private JdbcTemplate jdbc;

  private MockMvc mvc;
  private final ConcurrentLinkedQueue<String> registeredEmails =
    new ConcurrentLinkedQueue<>();

  @BeforeEach
  void setUpMockMvc() {
    mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
  }

  @AfterEach
  void removeTestUsersAndSessions() {
    for (String email : registeredEmails) {
      jdbc.update(
        "delete from deposit_sessions where user_id in (select id from app_users where email = ?)",
        email
      );
      jdbc.update("delete from app_users where email = ?", email);
    }
    registeredEmails.clear();
  }

  private Cookie registerUser() throws Exception {
    String email = "it-" + UUID.randomUUID() + "@monomates.test";
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
    Cookie cookie = result.getResponse().getCookie("mm_access_token");
    assertThat(cookie).as("auth cookie must be issued on register").isNotNull();
    return cookie;
  }

  private MvcResult startSession(Cookie auth, String binCode) throws Exception {
    return mvc
      .perform(post("/api/v1/bins/{code}/sessions", binCode).with(csrf()).cookie(auth))
      .andReturn();
  }

  private void cancelSession(Cookie auth, String sessionId) throws Exception {
    mvc
      .perform(post("/api/v1/sessions/{id}/cancel", sessionId).with(csrf()).cookie(auth))
      .andExpect(status().isOk());
  }

  private String field(MvcResult result, String name) throws Exception {
    return json
      .readTree(result.getResponse().getContentAsString())
      .path(name)
      .asText();
  }

  @Test
  void startingASessionDoesNotAwardAnyToken() throws Exception {
    Cookie auth = registerUser();

    mvc
      .perform(get("/api/v1/users/me/token-balance").cookie(auth))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.balance").value(0));

    MvcResult started = startSession(auth, ACTIVE_BIN);
    assertThat(started.getResponse().getStatus()).isEqualTo(200);
    assertThat(field(started, "status")).isEqualTo("ACTIVE");

    mvc
      .perform(get("/api/v1/users/me/token-balance").cookie(auth))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.balance").value(0));

    cancelSession(auth, field(started, "sessionId"));
  }

  @Test
  void secondSessionOnSameBinIsRejectedWhileFirstIsActive() throws Exception {
    Cookie ownerA = registerUser();
    Cookie ownerB = registerUser();

    MvcResult first = startSession(ownerA, SECOND_ACTIVE_BIN);
    assertThat(first.getResponse().getStatus()).isEqualTo(200);

    MvcResult second = startSession(ownerB, SECOND_ACTIVE_BIN);
    assertThat(second.getResponse().getStatus()).isEqualTo(409);

    cancelSession(ownerA, field(first, "sessionId"));
  }

  @Test
  void sessionIsNotVisibleToAUserWhoDoesNotOwnIt() throws Exception {
    Cookie owner = registerUser();
    Cookie stranger = registerUser();

    MvcResult started = startSession(owner, ACTIVE_BIN);
    assertThat(started.getResponse().getStatus()).isEqualTo(200);
    String sessionId = field(started, "sessionId");

    mvc
      .perform(get("/api/v1/sessions/{id}", sessionId).cookie(stranger))
      .andExpect(status().isNotFound());

    mvc
      .perform(post("/api/v1/sessions/{id}/cancel", sessionId).with(csrf()).cookie(owner))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.status").value("CANCELLED"));
  }

  @Test
  void binUnderMaintenanceRefusesNewSessions() throws Exception {
    Cookie user = registerUser();

    mvc
      .perform(post("/api/v1/bins/{code}/sessions", MAINTENANCE_BIN).with(csrf()).cookie(user))
      .andExpect(status().isUnprocessableEntity());
  }

  @Test
  void cancellingFreesTheBinImmediatelyForAnyoneIncludingTheSameUserTheSameDay()
    throws Exception {
    Cookie first = registerUser();
    Cookie second = registerUser();

    MvcResult started = startSession(first, ACTIVE_BIN);
    assertThat(started.getResponse().getStatus()).isEqualTo(200);
    cancelSession(first, field(started, "sessionId"));

    // A different user may now use the bin immediately.
    MvcResult nextUser = startSession(second, ACTIVE_BIN);
    assertThat(nextUser.getResponse().getStatus()).isEqualTo(200);
    assertThat(field(nextUser, "status")).isEqualTo("ACTIVE");
    cancelSession(second, field(nextUser, "sessionId"));

    // There is no longer a once-a-day-per-bin limit: the original user may
    // reuse the same bin again the same day, counted only against their
    // overall daily scan total (see the dedicated daily-limit test).
    MvcResult retry = startSession(first, ACTIVE_BIN);
    assertThat(retry.getResponse().getStatus()).isEqualTo(200);
    cancelSession(first, field(retry, "sessionId"));
  }

  @Test
  void aRegularAccountIsBlockedAfterTenScansTheSameDay() throws Exception {
    Cookie user = registerUser();

    for (int i = 0; i < 10; i++) {
      MvcResult started = startSession(user, i % 2 == 0 ? ACTIVE_BIN : SECOND_ACTIVE_BIN);
      assertThat(started.getResponse().getStatus())
        .as("scan #%d of 10 should be allowed", i + 1)
        .isEqualTo(200);
      cancelSession(user, field(started, "sessionId"));
    }

    MvcResult eleventh = startSession(user, ACTIVE_BIN);
    assertThat(eleventh.getResponse().getStatus()).isEqualTo(409);
    assertThat(field(eleventh, "message")).contains("10 scans for today");
  }

  @Test
  void exactlyOneOfTwoConcurrentSessionStartsOnTheSameBinSucceeds() throws Exception {
    Cookie userA = registerUser();
    Cookie userB = registerUser();

    ExecutorService pool = Executors.newFixedThreadPool(2);
    CyclicBarrier barrier = new CyclicBarrier(2);
    AtomicInteger okCount = new AtomicInteger();
    AtomicInteger conflictCount = new AtomicInteger();

    MvcResult resultA;
    MvcResult resultB;
    try {
      Future<MvcResult> a = pool.submit(() -> {
        barrier.await(5, TimeUnit.SECONDS);
        return startSession(userA, ACTIVE_BIN);
      });
      Future<MvcResult> b = pool.submit(() -> {
        barrier.await(5, TimeUnit.SECONDS);
        return startSession(userB, ACTIVE_BIN);
      });

      resultA = a.get(10, TimeUnit.SECONDS);
      resultB = b.get(10, TimeUnit.SECONDS);
    } finally {
      pool.shutdownNow();
    }

    for (MvcResult r : new MvcResult[] { resultA, resultB }) {
      int status = r.getResponse().getStatus();
      if (status == 200) okCount.incrementAndGet();
      else if (status == 409) conflictCount.incrementAndGet();
    }

    assertThat(okCount.get()).as("exactly one concurrent start should win").isEqualTo(1);
    assertThat(conflictCount.get()).as("the other must be rejected as a conflict").isEqualTo(1);

    MvcResult winner = resultA.getResponse().getStatus() == 200 ? resultA : resultB;
    Cookie winnerAuth = resultA.getResponse().getStatus() == 200 ? userA : userB;
    cancelSession(winnerAuth, field(winner, "sessionId"));
  }
}
