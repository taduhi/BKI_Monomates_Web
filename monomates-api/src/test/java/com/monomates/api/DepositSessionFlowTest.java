package com.monomates.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import jakarta.servlet.http.Cookie;
import java.time.Duration;
import java.time.Instant;
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
  void itemScanRequestIsOwnedAndIdempotent() throws Exception {
    Cookie owner = registerUser();
    Cookie stranger = registerUser();
    MvcResult started = startSession(owner, ACTIVE_BIN);
    String sessionId = field(started, "sessionId");

    mvc
      .perform(post("/api/v1/sessions/{id}/scan", sessionId).with(csrf()).cookie(stranger))
      .andExpect(status().isNotFound());

    MvcResult requested = mvc
      .perform(post("/api/v1/sessions/{id}/scan", sessionId).with(csrf()).cookie(owner))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.status").value("ACTIVE"))
      .andReturn();
    String requestedAt = field(requested, "scanRequestedAt");
    assertThat(requestedAt).isNotBlank();

    MvcResult repeated = mvc
      .perform(post("/api/v1/sessions/{id}/scan", sessionId).with(csrf()).cookie(owner))
      .andExpect(status().isOk())
      .andReturn();
    Duration timestampDifference = Duration.between(
      Instant.parse(requestedAt),
      Instant.parse(field(repeated, "scanRequestedAt"))
    ).abs();
    assertThat(timestampDifference).isLessThanOrEqualTo(Duration.ofNanos(1_000));

    cancelSession(owner, sessionId);
    mvc
      .perform(post("/api/v1/sessions/{id}/scan", sessionId).with(csrf()).cookie(owner))
      .andExpect(status().isUnprocessableEntity());
  }

  @Test
  void secondSessionOnSameBinAttachesToTheExistingActiveOne() throws Exception {
    Cookie ownerA = registerUser();
    Cookie ownerB = registerUser();

    MvcResult first = startSession(ownerA, SECOND_ACTIVE_BIN);
    assertThat(first.getResponse().getStatus()).isEqualTo(200);
    String sessionId = field(first, "sessionId");

    // There is only one physical rig per bin — a second person opening the
    // same bin attaches to the session already in progress instead of being
    // rejected, so anyone (e.g. the demo account standing in for the
    // hardware) can watch it resolve in real time.
    MvcResult second = startSession(ownerB, SECOND_ACTIVE_BIN);
    assertThat(second.getResponse().getStatus()).isEqualTo(200);
    assertThat(field(second, "sessionId")).isEqualTo(sessionId);

    cancelSession(ownerA, sessionId);
  }

  @Test
  void sessionIsVisibleToAnyoneButOnlyCancellableByItsOwner() throws Exception {
    Cookie owner = registerUser();
    Cookie stranger = registerUser();

    MvcResult started = startSession(owner, ACTIVE_BIN);
    assertThat(started.getResponse().getStatus()).isEqualTo(200);
    String sessionId = field(started, "sessionId");

    // Viewing is deliberately open to any signed-in account — nothing in a
    // session response is sensitive, and this is what lets someone else
    // (the demo account) watch a real user's deposit resolve.
    mvc
      .perform(get("/api/v1/sessions/{id}", sessionId).cookie(stranger))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.sessionId").value(sessionId));

    // Acting on it (cancelling) stays owner-only.
    mvc
      .perform(post("/api/v1/sessions/{id}/cancel", sessionId).with(csrf()).cookie(stranger))
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
  void anAdminAccountHasNoDailyScanLimit() throws Exception {
    Cookie admin = registerUser();
    jdbc.update("update app_users set role = 'ADMIN' where email = ?", registeredEmails.peek());

    // A regular account would be blocked on the 11th scan of the day (see
    // aRegularAccountIsBlockedAfterTenScansTheSameDay); an admin account
    // must still succeed well past that point, same as the fixed demo
    // account.
    for (int i = 0; i < 12; i++) {
      MvcResult started = startSession(admin, i % 2 == 0 ? ACTIVE_BIN : SECOND_ACTIVE_BIN);
      assertThat(started.getResponse().getStatus())
        .as("admin scan #%d should never be blocked by the daily limit", i + 1)
        .isEqualTo(200);
      mvc
        .perform(post("/api/v1/sessions/{id}/scan", field(started, "sessionId")).with(csrf()).cookie(admin))
        .andExpect(status().isOk());
      cancelSession(admin, field(started, "sessionId"));
    }
  }

  @Test
  void concurrentSessionStartsOnTheSameBinNeverProduceTwoDifferentActiveSessions()
    throws Exception {
    // Depending on exact thread scheduling, two truly simultaneous starts on
    // one bin resolve one of two legitimate ways:
    //  (a) one request's read happens to run after the other has already
    //      committed, so it takes the "attach instead of conflict" branch
    //      (see secondSessionOnSameBinAttachesToTheExistingActiveOne) —
    //      both calls return 200 with the identical sessionId; or
    //  (b) both reads run before either commits, neither sees an existing
    //      row, both attempt to insert, and the partial unique index on
    //      (bin_id) WHERE status = 'ACTIVE' — the final race-safety net,
    //      unchanged by this feature — rejects the loser with a conflict.
    // Which of the two happens is a timing accident, not a bug; what must
    // never happen is two different sessions both ending up ACTIVE for the
    // same bin, which this test asserts regardless of which path was hit.
    Cookie userA = registerUser();
    Cookie userB = registerUser();

    ExecutorService pool = Executors.newFixedThreadPool(2);
    CyclicBarrier barrier = new CyclicBarrier(2);

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

    int statusA = resultA.getResponse().getStatus();
    int statusB = resultB.getResponse().getStatus();

    if (statusA == 200 && statusB == 200) {
      assertThat(field(resultA, "sessionId"))
        .as("two 200s on the same bin must be the same session, never two different ones")
        .isEqualTo(field(resultB, "sessionId"));
      // Whichever of A/B actually won the race to create the row owns it —
      // that is a timing accident too, so try both rather than assuming A.
      String sessionId = field(resultA, "sessionId");
      int cancelStatusA = mvc
        .perform(post("/api/v1/sessions/{id}/cancel", sessionId).with(csrf()).cookie(userA))
        .andReturn()
        .getResponse()
        .getStatus();
      if (cancelStatusA != 200) {
        assertThat(cancelStatusA)
          .as("whichever of A/B did not create the session cannot cancel it")
          .isEqualTo(404);
        cancelSession(userB, sessionId);
      }
    } else {
      assertThat(statusA == 200 || statusB == 200)
        .as("at least one concurrent start must win")
        .isTrue();
      assertThat(statusA == 409 || statusB == 409)
        .as("the loser of a genuine race must be reported as a conflict")
        .isTrue();
      MvcResult winner = statusA == 200 ? resultA : resultB;
      Cookie winnerAuth = statusA == 200 ? userA : userB;
      cancelSession(winnerAuth, field(winner, "sessionId"));
    }
  }
}
