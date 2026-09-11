package com.monomates.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import jakarta.servlet.http.Cookie;
import java.util.ArrayList;
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
 * Covers the admin bin write path added alongside the delete/edit-anything
 * request: a bin's public code may now change after creation (as long as it
 * does not collide with another bin), and a bin with no devices/deposit
 * history can be permanently deleted, while one with real history is
 * refused rather than silently cascading into hardware/reward records.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@ActiveProfiles("local")
class AdminBinFlowTest {

  @Autowired
  private WebApplicationContext context;

  @Autowired
  private ObjectMapper json;

  @Autowired
  private JdbcTemplate jdbc;

  private MockMvc mvc;
  private final List<UUID> createdBinIds = new ArrayList<>();

  @BeforeEach
  void setUpMockMvc() {
    mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
  }

  @AfterEach
  void removeAnyBinLeftBehind() {
    for (UUID id : createdBinIds) {
      List<UUID> locationIds = jdbc.queryForList(
        "select location_id from bins where id = ?",
        UUID.class,
        id
      );
      jdbc.update("delete from bin_accepted_items where bin_id = ?", id);
      jdbc.update(
        "delete from device_events where device_id in (select id from devices where bin_id = ?)",
        id
      );
      jdbc.update("delete from devices where bin_id = ?", id);
      jdbc.update("delete from bins where id = ?", id);
      for (UUID locationId : locationIds) {
        jdbc.update("delete from locations where id = ?", locationId);
      }
    }
    createdBinIds.clear();
  }

  private Cookie adminAuth() throws Exception {
    MvcResult login = mvc
      .perform(
        post("/api/v1/auth/login")
          .with(csrf())
          .contentType(MediaType.APPLICATION_JSON)
          .content("{\"email\":\"admin@monomates.local\",\"password\":\"Admin123!\"}")
      )
      .andExpect(status().isOk())
      .andReturn();
    return login.getResponse().getCookie("mm_access_token");
  }

  private String binPayload(String code, String name) {
    return """
      {"publicCode":"%s","name":"%s","status":"ACTIVE","capacityPercent":10,
       "locationName":"Test site","address":"123 Test Street",
       "latitude":10.0,"longitude":106.0,"acceptedItemCodes":["CLEAR_PET_BOTTLE"]}
      """.formatted(code, name);
  }

  private MvcResult createBin(Cookie admin, String code) throws Exception {
    MvcResult created = mvc
      .perform(
        post("/api/v1/admin/bins")
          .with(csrf())
          .cookie(admin)
          .contentType(MediaType.APPLICATION_JSON)
          .content(binPayload(code, "Admin Bin Flow Test"))
      )
      .andExpect(status().isOk())
      .andReturn();
    UUID id = UUID.fromString(
      json.readTree(created.getResponse().getContentAsString()).path("id").asText()
    );
    createdBinIds.add(id);
    return created;
  }

  @Test
  void adminCanRenameABinsPublicCodeAfterCreation() throws Exception {
    Cookie admin = adminAuth();
    String originalCode = "it-rename-" + UUID.randomUUID().toString().substring(0, 8);
    MvcResult created = createBin(admin, originalCode);
    UUID id = UUID.fromString(
      json.readTree(created.getResponse().getContentAsString()).path("id").asText()
    );

    String newCode = originalCode + "-renamed";
    mvc
      .perform(
        patch("/api/v1/admin/bins/{id}", id)
          .with(csrf())
          .cookie(admin)
          .contentType(MediaType.APPLICATION_JSON)
          .content(binPayload(newCode, "Admin Bin Flow Test Renamed"))
      )
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.publicCode").value(newCode));

    mvc
      .perform(get("/api/v1/bins/{code}", originalCode))
      .andExpect(status().isNotFound());
    mvc
      .perform(get("/api/v1/bins/{code}", newCode))
      .andExpect(status().isOk());
  }

  @Test
  void renamingToAnotherBinsExistingCodeIsRejected() throws Exception {
    Cookie admin = adminAuth();
    String codeA = "it-collide-a-" + UUID.randomUUID().toString().substring(0, 8);
    String codeB = "it-collide-b-" + UUID.randomUUID().toString().substring(0, 8);
    createBin(admin, codeA);
    MvcResult binB = createBin(admin, codeB);
    UUID idB = UUID.fromString(
      json.readTree(binB.getResponse().getContentAsString()).path("id").asText()
    );

    mvc
      .perform(
        patch("/api/v1/admin/bins/{id}", idB)
          .with(csrf())
          .cookie(admin)
          .contentType(MediaType.APPLICATION_JSON)
          .content(binPayload(codeA, "Admin Bin Flow Test"))
      )
      .andExpect(status().isConflict());
  }

  @Test
  void aBinWithNoHistoryCanBeDeletedPermanently() throws Exception {
    Cookie admin = adminAuth();
    String code = "it-delete-" + UUID.randomUUID().toString().substring(0, 8);
    MvcResult created = createBin(admin, code);
    UUID id = UUID.fromString(
      json.readTree(created.getResponse().getContentAsString()).path("id").asText()
    );

    mvc
      .perform(delete("/api/v1/admin/bins/{id}", id).with(csrf()).cookie(admin))
      .andExpect(status().isNoContent());

    mvc.perform(get("/api/v1/bins/{code}", code)).andExpect(status().isNotFound());
    assertThat(jdbc.queryForObject("select count(*) from bins where id = ?", Integer.class, id))
      .isZero();

    // Deletion already succeeded — nothing left for @AfterEach to clean up.
    createdBinIds.remove(id);
  }

  @Test
  void creatingABinProvisionsADeviceSoTheSecretSortControlsWorkOnIt() throws Exception {
    Cookie admin = adminAuth();
    String code = "it-device-" + UUID.randomUUID().toString().substring(0, 8);
    MvcResult created = createBin(admin, code);
    UUID id = UUID.fromString(
      json.readTree(created.getResponse().getContentAsString()).path("id").asText()
    );
    assertThat(jdbc.queryForObject("select count(*) from devices where bin_id = ?", Integer.class, id))
      .as("a new bin must get a device automatically, or the demo secret-sort controls have nothing to act on")
      .isEqualTo(1);

    Cookie demo = mvc
      .perform(
        post("/api/v1/auth/login")
          .with(csrf())
          .contentType(MediaType.APPLICATION_JSON)
          .content("{\"email\":\"demo@monomates.app\",\"password\":\"monomates1\"}")
      )
      .andExpect(status().isOk())
      .andReturn()
      .getResponse()
      .getCookie("mm_access_token");

    MvcResult session = mvc
      .perform(post("/api/v1/bins/{code}/sessions", code).with(csrf()).cookie(demo))
      .andExpect(status().isOk())
      .andReturn();
    String sessionId = json
      .readTree(session.getResponse().getContentAsString())
      .path("sessionId")
      .asText();

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

    UUID deviceEventId = jdbc.queryForObject(
      "select device_event_id from deposits where session_id = ?",
      UUID.class,
      UUID.fromString(sessionId)
    );
    jdbc.update(
      "delete from token_ledger where deposit_id in (select id from deposits where session_id = ?)",
      UUID.fromString(sessionId)
    );
    jdbc.update("delete from deposits where session_id = ?", UUID.fromString(sessionId));
    jdbc.update("delete from device_events where id = ?", deviceEventId);
    jdbc.update("delete from deposit_sessions where id = ?", UUID.fromString(sessionId));
  }

  @Test
  void aBinWithExistingDepositHistoryCannotBeDeleted() throws Exception {
    Cookie admin = adminAuth();

    MvcResult binResponse = mvc
      .perform(get("/api/v1/bins/{code}", "BIN-HCMUT-001"))
      .andExpect(status().isOk())
      .andReturn();
    UUID id = UUID.fromString(
      json.readTree(binResponse.getResponse().getContentAsString()).path("id").asText()
    );

    mvc
      .perform(delete("/api/v1/admin/bins/{id}", id).with(csrf()).cookie(admin))
      .andExpect(status().isUnprocessableEntity());

    mvc.perform(get("/api/v1/bins/{code}", "BIN-HCMUT-001")).andExpect(status().isOk());
  }
}
