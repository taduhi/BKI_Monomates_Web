package com.monomates.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.servlet.http.Cookie;
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
import tools.jackson.databind.ObjectMapper;

/**
 * M7: voucher redemption safety, exercised over the real HTTP-shaped
 * endpoints and real PostgreSQL (Flyway V1-V5). Regression-tests the
 * CX-009/CX-010 design: every redemption locks the user row before the
 * voucher row, serializing a user's concurrent redemptions across
 * DIFFERENT vouchers (not just repeats of the same voucher).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@ActiveProfiles("local")
class VoucherRedemptionFlowTest {

  @Autowired
  private WebApplicationContext context;

  @Autowired
  private ObjectMapper json;

  @Autowired
  private JdbcTemplate jdbc;

  private MockMvc mvc;
  private final ConcurrentLinkedQueue<String> registeredEmails = new ConcurrentLinkedQueue<>();
  private final ConcurrentLinkedQueue<UUID> createdVoucherIds = new ConcurrentLinkedQueue<>();

  @BeforeEach
  void setUpMockMvc() {
    mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
  }

  @AfterEach
  void cleanUp() {
    for (String email : registeredEmails) {
      jdbc.update(
        "delete from token_ledger where user_id in (select id from app_users where email = ?)",
        email
      );
      jdbc.update(
        "delete from redemptions where user_id in (select id from app_users where email = ?)",
        email
      );
      jdbc.update("delete from app_users where email = ?", email);
    }
    registeredEmails.clear();
    for (UUID voucherId : createdVoucherIds) {
      jdbc.update("delete from token_ledger where redemption_id in (select id from redemptions where voucher_id = ?)", voucherId);
      jdbc.update("delete from redemptions where voucher_id = ?", voucherId);
      jdbc.update("delete from vouchers where id = ?", voucherId);
    }
    createdVoucherIds.clear();
  }

  private record TestUser(Cookie auth, UUID id) {}

  private TestUser registerUser() throws Exception {
    String email = "it-voucher-" + UUID.randomUUID() + "@monomates.test";
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
    UUID userId = UUID.fromString(
      json.readTree(result.getResponse().getContentAsString()).path("user").path("id").asText()
    );
    return new TestUser(cookie, userId);
  }

  private Cookie loginAdmin() throws Exception {
    MvcResult result = mvc
      .perform(
        post("/api/v1/auth/login")
          .with(csrf())
          .contentType(MediaType.APPLICATION_JSON)
          .content("{\"email\":\"admin@monomates.local\",\"password\":\"Admin123!\"}")
      )
      .andExpect(status().isOk())
      .andReturn();
    return result.getResponse().getCookie("mm_access_token");
  }

  private void grantBalance(UUID userId, int amount) {
    jdbc.update(
      """
      insert into token_ledger
        (id, user_id, deposit_id, redemption_id, transaction_type, amount, description, created_at, updated_at)
      values (?, ?, null, null, 'ADJUSTMENT', ?, 'test seed balance', now(), now())
      """,
      UUID.randomUUID(),
      userId,
      amount
    );
  }

  private UUID createVoucher(Cookie adminAuth, int tokenCost, int inventory, String status) throws Exception {
    String body = """
      {
        "partnerName": "Test Partner",
        "title": "Integration Test Voucher %s",
        "description": "seeded by test",
        "tokenCost": %d,
        "inventory": %d,
        "status": "%s"
      }
      """.formatted(UUID.randomUUID(), tokenCost, inventory, status);
    MvcResult result = mvc
      .perform(
        post("/api/v1/admin/vouchers")
          .with(csrf())
          .cookie(adminAuth)
          .contentType(MediaType.APPLICATION_JSON)
          .content(body)
      )
      .andExpect(status().isOk())
      .andReturn();
    UUID id = UUID.fromString(json.readTree(result.getResponse().getContentAsString()).path("id").asText());
    createdVoucherIds.add(id);
    return id;
  }

  private MvcResult redeem(Cookie auth, UUID voucherId) throws Exception {
    return mvc
      .perform(post("/api/v1/vouchers/{id}/redeem", voucherId).with(csrf()).cookie(auth))
      .andReturn();
  }

  private long balanceOf(UUID userId) {
    Long value = jdbc.queryForObject(
      "select coalesce(sum(amount), 0) from token_ledger where user_id = ?",
      Long.class,
      userId
    );
    return value == null ? 0 : value;
  }

  @Test
  void sameUserCannotRedeemTwoVouchersWhenBalanceOnlyCoversOne() throws Exception {
    Cookie admin = loginAdmin();
    TestUser buyer = registerUser();
    grantBalance(buyer.id(), 30);

    UUID voucherA = createVoucher(admin, 30, 5, "ACTIVE");
    UUID voucherB = createVoucher(admin, 30, 5, "ACTIVE");

    ExecutorService pool = Executors.newFixedThreadPool(2);
    try {
      CyclicBarrier barrier = new CyclicBarrier(2);
      Callable<MvcResult> attemptA = () -> {
        barrier.await(5, TimeUnit.SECONDS);
        return redeem(buyer.auth(), voucherA);
      };
      Callable<MvcResult> attemptB = () -> {
        barrier.await(5, TimeUnit.SECONDS);
        return redeem(buyer.auth(), voucherB);
      };
      Future<MvcResult> a = pool.submit(attemptA);
      Future<MvcResult> b = pool.submit(attemptB);
      MvcResult resultA = a.get(10, TimeUnit.SECONDS);
      MvcResult resultB = b.get(10, TimeUnit.SECONDS);

      AtomicInteger okCount = new AtomicInteger();
      AtomicInteger rejectedCount = new AtomicInteger();
      for (MvcResult r : new MvcResult[] { resultA, resultB }) {
        int s = r.getResponse().getStatus();
        if (s == 200) okCount.incrementAndGet();
        else if (s == 422) rejectedCount.incrementAndGet();
      }
      assertThat(okCount.get()).as("exactly one of the two redemptions must succeed").isEqualTo(1);
      assertThat(rejectedCount.get()).as("the other must be rejected as insufficient balance").isEqualTo(1);
    } finally {
      pool.shutdownNow();
    }

    assertThat(balanceOf(buyer.id())).as("balance must never go negative").isEqualTo(0L);
    Long redemptionCount = jdbc.queryForObject(
      "select count(*) from redemptions where user_id = ?",
      Long.class,
      buyer.id()
    );
    assertThat(redemptionCount).as("exactly one redemption row").isEqualTo(1L);
    Long ledgerDeductionCount = jdbc.queryForObject(
      "select count(*) from token_ledger where user_id = ? and transaction_type = 'REDEMPTION'",
      Long.class,
      buyer.id()
    );
    assertThat(ledgerDeductionCount).as("exactly one deduction").isEqualTo(1L);
  }

  @Test
  void twoUsersCannotBothRedeemTheLastUnitOfAVoucher() throws Exception {
    Cookie admin = loginAdmin();
    TestUser userA = registerUser();
    TestUser userB = registerUser();
    grantBalance(userA.id(), 50);
    grantBalance(userB.id(), 50);

    UUID voucher = createVoucher(admin, 10, 1, "ACTIVE");

    ExecutorService pool = Executors.newFixedThreadPool(2);
    try {
      CyclicBarrier barrier = new CyclicBarrier(2);
      Callable<MvcResult> attemptA = () -> {
        barrier.await(5, TimeUnit.SECONDS);
        return redeem(userA.auth(), voucher);
      };
      Callable<MvcResult> attemptB = () -> {
        barrier.await(5, TimeUnit.SECONDS);
        return redeem(userB.auth(), voucher);
      };
      Future<MvcResult> a = pool.submit(attemptA);
      Future<MvcResult> b = pool.submit(attemptB);
      MvcResult resultA = a.get(10, TimeUnit.SECONDS);
      MvcResult resultB = b.get(10, TimeUnit.SECONDS);

      AtomicInteger okCount = new AtomicInteger();
      for (MvcResult r : new MvcResult[] { resultA, resultB }) {
        if (r.getResponse().getStatus() == 200) okCount.incrementAndGet();
        else assertThat(r.getResponse().getStatus()).isEqualTo(422);
      }
      assertThat(okCount.get()).as("exactly one user should win the last unit").isEqualTo(1);
    } finally {
      pool.shutdownNow();
    }

    Integer inventory = jdbc.queryForObject(
      "select inventory from vouchers where id = ?",
      Integer.class,
      voucher
    );
    assertThat(inventory).as("inventory must never go negative").isEqualTo(0);
    Long redemptionCount = jdbc.queryForObject(
      "select count(*) from redemptions where voucher_id = ?",
      Long.class,
      voucher
    );
    assertThat(redemptionCount).as("exactly one redemption for this voucher").isEqualTo(1L);
  }

  @Test
  void insufficientBalanceIsRejected() throws Exception {
    Cookie admin = loginAdmin();
    TestUser buyer = registerUser();
    grantBalance(buyer.id(), 5);
    UUID voucher = createVoucher(admin, 10, 5, "ACTIVE");

    MvcResult result = redeem(buyer.auth(), voucher);
    assertThat(result.getResponse().getStatus()).isEqualTo(422);
  }

  @Test
  void inactiveVoucherIsRejected() throws Exception {
    Cookie admin = loginAdmin();
    TestUser buyer = registerUser();
    grantBalance(buyer.id(), 100);
    UUID voucher = createVoucher(admin, 10, 5, "INACTIVE");

    MvcResult result = redeem(buyer.auth(), voucher);
    assertThat(result.getResponse().getStatus()).isEqualTo(422);
  }

  @Test
  void outOfStockVoucherIsRejected() throws Exception {
    Cookie admin = loginAdmin();
    TestUser buyer = registerUser();
    grantBalance(buyer.id(), 100);
    UUID voucher = createVoucher(admin, 10, 0, "ACTIVE");

    MvcResult result = redeem(buyer.auth(), voucher);
    assertThat(result.getResponse().getStatus()).isEqualTo(422);
  }

  @Test
  void successfulRedemptionReturnsCodeAndRemainingBalance() throws Exception {
    Cookie admin = loginAdmin();
    TestUser buyer = registerUser();
    grantBalance(buyer.id(), 40);
    UUID voucher = createVoucher(admin, 15, 5, "ACTIVE");

    MvcResult result = redeem(buyer.auth(), voucher);
    assertThat(result.getResponse().getStatus()).isEqualTo(200);
    var body = json.readTree(result.getResponse().getContentAsString());
    assertThat(body.path("redemptionCode").asText()).startsWith("MM-");
    assertThat(body.path("remainingBalance").asLong()).isEqualTo(25L);
    assertThat(balanceOf(buyer.id())).isEqualTo(25L);

    mvc
      .perform(get("/api/v1/users/me/redemptions").cookie(buyer.auth()))
      .andExpect(status().isOk())
      .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$[0].redemptionCode").value(body.path("redemptionCode").asText()));
  }

  @Test
  void defaultPerUserLimitPreventsRedeemingTheSameVoucherTwice() throws Exception {
    Cookie admin = loginAdmin();
    TestUser buyer = registerUser();
    grantBalance(buyer.id(), 100);
    UUID voucher = createVoucher(admin, 10, 5, "ACTIVE");

    assertThat(redeem(buyer.auth(), voucher).getResponse().getStatus()).isEqualTo(200);
    MvcResult second = redeem(buyer.auth(), voucher);

    assertThat(second.getResponse().getStatus()).isEqualTo(422);
    assertThat(second.getResponse().getContentAsString()).contains("redemption limit");
    assertThat(balanceOf(buyer.id())).isEqualTo(90L);
  }
}
