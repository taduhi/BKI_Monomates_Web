package com.monomates.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.servlet.http.Cookie;
import java.util.List;
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
import tools.jackson.databind.ObjectMapper;

/**
 * M4-M5: simulated hardware deposit events, reward outcomes and event
 * idempotency, exercised over the real HTTP-shaped device endpoint and the
 * real PostgreSQL schema (Flyway V1-V3). {@code /api/v1/device/**} is
 * permitAll and CSRF-exempt, matching real hardware clients.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@ActiveProfiles("local")
class DeviceDepositEventFlowTest {

  private static final String ACTIVE_BIN = "BIN-HCMUT-001";
  private static final String DEVICE_CODE = "DEV-HCMUT-001";
  private static final String DEVICE_SECRET = "demo-device-secret-hcmut";
  private static final String OTHER_BIN_DEVICE_CODE = "DEV-YOUTH-001";
  private static final String OTHER_BIN_DEVICE_SECRET = "demo-device-secret-youth";

  @Autowired
  private WebApplicationContext context;

  @Autowired
  private ObjectMapper json;

  @Autowired
  private JdbcTemplate jdbc;

  private MockMvc mvc;
  private final ConcurrentLinkedQueue<String> registeredEmails = new ConcurrentLinkedQueue<>();

  @BeforeEach
  void setUpMockMvc() {
    mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
  }

  @AfterEach
  void cleanUp() {
    for (String email : registeredEmails) {
      List<UUID> eventIds = jdbc.queryForList(
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

  private Cookie registerUser() throws Exception {
    String email = "it-device-" + UUID.randomUUID() + "@monomates.test";
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
    assertThat(cookie).isNotNull();
    return cookie;
  }

  private String startSession(Cookie auth, String binCode) throws Exception {
    MvcResult result = mvc
      .perform(post("/api/v1/bins/{code}/sessions", binCode).with(csrf()).cookie(auth))
      .andExpect(status().isOk())
      .andReturn();
    return field(result, "sessionId");
  }

  private String field(MvcResult result, String name) throws Exception {
    return json.readTree(result.getResponse().getContentAsString()).path(name).asText();
  }

  private int intField(MvcResult result, String name) throws Exception {
    return json.readTree(result.getResponse().getContentAsString()).path(name).asInt();
  }

  private MvcResult submitEvent(
    String deviceCode,
    String deviceSecret,
    String eventId,
    String sessionId,
    boolean irDetected,
    String weightGrams,
    String itemType,
    String confidence
  ) throws Exception {
    String body = """
      {
        "deviceCode": "%s",
        "deviceSecret": "%s",
        "eventId": "%s",
        "sessionId": "%s",
        "irDetected": %s,
        "weightChangeGrams": %s,
        "itemType": %s,
        "classificationConfidence": %s
      }
      """.formatted(
      deviceCode,
      deviceSecret,
      eventId,
      sessionId,
      irDetected,
      weightGrams,
      itemType == null ? "null" : "\"" + itemType + "\"",
      confidence
    );
    return mvc
      .perform(post("/api/v1/device/events/deposit").contentType(MediaType.APPLICATION_JSON).content(body))
      .andReturn();
  }

  @Test
  void acceptedClearPetBottleEarnsTwoTokens() throws Exception {
    Cookie auth = registerUser();
    String sessionId = startSession(auth, ACTIVE_BIN);

    MvcResult result = submitEvent(
      DEVICE_CODE,
      DEVICE_SECRET,
      "evt-" + UUID.randomUUID(),
      sessionId,
      true,
      "24.0",
      "CLEAR_PET_BOTTLE",
      "0.95"
    );

    assertThat(result.getResponse().getStatus()).isEqualTo(200);
    assertThat(field(result, "status")).isEqualTo("ACCEPTED");
    assertThat(intField(result, "tokensAwarded")).isEqualTo(2);
  }

  @Test
  void validButUnclassifiedDepositEarnsOneToken() throws Exception {
    Cookie auth = registerUser();
    String sessionId = startSession(auth, ACTIVE_BIN);

    MvcResult result = submitEvent(
      DEVICE_CODE,
      DEVICE_SECRET,
      "evt-" + UUID.randomUUID(),
      sessionId,
      true,
      "18.0",
      "CLEAR_PET_BOTTLE",
      "0.40"
    );

    assertThat(result.getResponse().getStatus()).isEqualTo(200);
    assertThat(field(result, "status")).isEqualTo("VALID_UNCLASSIFIED");
    assertThat(intField(result, "tokensAwarded")).isEqualTo(1);
  }

  @Test
  void noIrDetectionEarnsNoToken() throws Exception {
    Cookie auth = registerUser();
    String sessionId = startSession(auth, ACTIVE_BIN);

    MvcResult result = submitEvent(
      DEVICE_CODE,
      DEVICE_SECRET,
      "evt-" + UUID.randomUUID(),
      sessionId,
      false,
      "0.0",
      null,
      null
    );

    assertThat(result.getResponse().getStatus()).isEqualTo(200);
    assertThat(field(result, "status")).isEqualTo("REJECTED");
    assertThat(intField(result, "tokensAwarded")).isEqualTo(0);
  }

  @Test
  void eventFromADeviceOnAnotherBinIsRejected() throws Exception {
    Cookie auth = registerUser();
    String sessionId = startSession(auth, ACTIVE_BIN);

    MvcResult result = submitEvent(
      OTHER_BIN_DEVICE_CODE,
      OTHER_BIN_DEVICE_SECRET,
      "evt-" + UUID.randomUUID(),
      sessionId,
      true,
      "24.0",
      "CLEAR_PET_BOTTLE",
      "0.95"
    );

    assertThat(result.getResponse().getStatus()).isEqualTo(422);
  }

  @Test
  void eventWithoutAnExistingSessionCreatesNoEventAndAwardsNoToken() throws Exception {
    Cookie auth = registerUser();
    String eventId = "evt-" + UUID.randomUUID();

    MvcResult result = submitEvent(
      DEVICE_CODE,
      DEVICE_SECRET,
      eventId,
      UUID.randomUUID().toString(),
      true,
      "24.0",
      "CLEAR_PET_BOTTLE",
      "0.95"
    );

    assertThat(result.getResponse().getStatus()).isEqualTo(404);
    MvcResult balance = mvc
      .perform(get("/api/v1/users/me/token-balance").cookie(auth))
      .andExpect(status().isOk())
      .andReturn();
    assertThat(intField(balance, "balance")).isZero();
    Long storedEvents = jdbc.queryForObject(
      """
      select count(*) from device_events e
      join devices d on d.id = e.device_id
      where d.device_code = ? and e.external_event_id = ?
      """,
      Long.class,
      DEVICE_CODE,
      eventId
    );
    assertThat(storedEvents).isZero();
  }

  @Test
  void repeatingTheSameEventIdReturnsTheSameResultWithoutANewLedgerEntry() throws Exception {
    Cookie auth = registerUser();
    String sessionId = startSession(auth, ACTIVE_BIN);
    String eventId = "evt-" + UUID.randomUUID();

    MvcResult first = submitEvent(DEVICE_CODE, DEVICE_SECRET, eventId, sessionId, true, "24.0", "CLEAR_PET_BOTTLE", "0.95");
    assertThat(first.getResponse().getStatus()).isEqualTo(200);
    String depositId = field(first, "id");

    MvcResult replay = submitEvent(DEVICE_CODE, DEVICE_SECRET, eventId, sessionId, true, "24.0", "CLEAR_PET_BOTTLE", "0.95");
    assertThat(replay.getResponse().getStatus()).isEqualTo(200);
    assertThat(field(replay, "id")).isEqualTo(depositId);
    assertThat(intField(replay, "tokensAwarded")).isEqualTo(2);

    Long ledgerRowsForDeposit = jdbc.queryForObject(
      "select count(*) from token_ledger where deposit_id = ?::uuid",
      Long.class,
      depositId
    );
    assertThat(ledgerRowsForDeposit).isEqualTo(2L); // DEPOSIT_BASE + PET_BONUS, exactly once each
  }

  @Test
  void repeatingAnEventIdForAnotherSessionIsRejected() throws Exception {
    Cookie firstUser = registerUser();
    String firstSessionId = startSession(firstUser, ACTIVE_BIN);
    String eventId = "evt-" + UUID.randomUUID();

    MvcResult first = submitEvent(
      DEVICE_CODE,
      DEVICE_SECRET,
      eventId,
      firstSessionId,
      true,
      "24.0",
      "CLEAR_PET_BOTTLE",
      "0.95"
    );
    assertThat(first.getResponse().getStatus()).isEqualTo(200);

    Cookie secondUser = registerUser();
    String secondSessionId = startSession(secondUser, ACTIVE_BIN);
    MvcResult conflictingReplay = submitEvent(
      DEVICE_CODE,
      DEVICE_SECRET,
      eventId,
      secondSessionId,
      true,
      "24.0",
      "CLEAR_PET_BOTTLE",
      "0.95"
    );

    assertThat(conflictingReplay.getResponse().getStatus()).isEqualTo(409);
    Long secondSessionDeposits = jdbc.queryForObject(
      "select count(*) from deposits where session_id = ?::uuid",
      Long.class,
      secondSessionId
    );
    assertThat(secondSessionDeposits).isZero();
  }

  @Test
  void exactlyOneOfTwoConcurrentIdenticalEventsCreatesTheDeposit() throws Exception {
    Cookie auth = registerUser();
    String sessionId = startSession(auth, ACTIVE_BIN);
    String eventId = "evt-" + UUID.randomUUID();

    ExecutorService pool = Executors.newFixedThreadPool(2);
    CyclicBarrier barrier = new CyclicBarrier(2);
    AtomicInteger okCount = new AtomicInteger();

    Callable<MvcResult> attempt = () -> {
      barrier.await(5, TimeUnit.SECONDS);
      return submitEvent(DEVICE_CODE, DEVICE_SECRET, eventId, sessionId, true, "24.0", "CLEAR_PET_BOTTLE", "0.95");
    };

    MvcResult resultA;
    MvcResult resultB;
    try {
      Future<MvcResult> a = pool.submit(attempt);
      Future<MvcResult> b = pool.submit(attempt);
      resultA = a.get(10, TimeUnit.SECONDS);
      resultB = b.get(10, TimeUnit.SECONDS);
    } finally {
      pool.shutdownNow();
    }

    for (MvcResult r : new MvcResult[] { resultA, resultB }) {
      if (r.getResponse().getStatus() == 200) okCount.incrementAndGet();
    }
    assertThat(okCount.get()).as("both concurrent duplicates must resolve to the same successful result").isEqualTo(2);
    assertThat(field(resultA, "id")).isEqualTo(field(resultB, "id"));
    assertThat(intField(resultA, "tokensAwarded")).isEqualTo(2);
    assertThat(intField(resultB, "tokensAwarded")).isEqualTo(2);

    Long depositCount = jdbc.queryForObject(
      "select count(*) from deposits where session_id = ?::uuid",
      Long.class,
      sessionId
    );
    assertThat(depositCount).as("only one deposit row for this session").isEqualTo(1L);

    Long ledgerCount = jdbc.queryForObject(
      "select count(*) from token_ledger where deposit_id = ?::uuid",
      Long.class,
      field(resultA, "id")
    );
    assertThat(ledgerCount).as("no duplicate reward from the race").isEqualTo(2L);
  }
}
