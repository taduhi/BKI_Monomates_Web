package com.monomates.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;

import jakarta.servlet.http.Cookie;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@ActiveProfiles("local")
class DeviceConnectionFlowTest {

  @Autowired private WebApplicationContext context;
  @Autowired private ObjectMapper json;

  private MockMvc mvc;

  @BeforeEach
  void setup() {
    mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
  }

  private Cookie login(String email, String password) throws Exception {
    return mvc
      .perform(
        post("/api/v1/auth/login")
          .with(csrf())
          .contentType(MediaType.APPLICATION_JSON)
          .content("{\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, password))
      )
      .andExpect(status().isOk())
      .andReturn()
      .getResponse()
      .getCookie("mm_access_token");
  }

  @Test
  void adminConfiguresConnectionAndHeartbeatReceivesPendingScan() throws Exception {
    Cookie admin = login("admin@monomates.local", "Admin123!");
    MvcResult bin = mvc
      .perform(get("/api/v1/bins/BIN-HCMUT-001"))
      .andExpect(status().isOk())
      .andReturn();
    UUID binId = UUID.fromString(
      json.readTree(bin.getResponse().getContentAsString()).path("id").asText()
    );

    mvc
      .perform(
        patch("/api/v1/admin/devices/{binId}/connection", binId)
          .with(csrf())
          .cookie(admin)
          .contentType(MediaType.APPLICATION_JSON)
          .content("{\"connectionMode\":\"AUTO\",\"bridgePort\":8000,\"acceptedDirection\":\"RIGHT\",\"swapDirections\":true}")
      )
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.connectionMode").value("AUTO"))
      .andExpect(jsonPath("$.bridgePort").value(8000))
      .andExpect(jsonPath("$.swapDirections").value(true));

    Cookie demo = login("demo@monomates.app", "monomates1");
    MvcResult started = mvc
      .perform(post("/api/v1/bins/BIN-HCMUT-001/sessions").with(csrf()).cookie(demo))
      .andExpect(status().isOk())
      .andReturn();
    String sessionId = json
      .readTree(started.getResponse().getContentAsString())
      .path("sessionId")
      .asText();
    try {
      mvc
        .perform(post("/api/v1/sessions/{id}/scan", sessionId).with(csrf()).cookie(demo))
        .andExpect(status().isOk());

      mvc
        .perform(
          post("/api/v1/device/heartbeat")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"deviceCode\":\"DEV-HCMUT-001\",\"deviceSecret\":\"demo-device-secret-hcmut\",\"firmwareVersion\":\"test\",\"transport\":\"WIFI\"}")
        )
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.command").value("SCAN_ITEM"))
        .andExpect(jsonPath("$.sessionId").value(sessionId))
        .andExpect(jsonPath("$.swapDirections").value(true))
        .andExpect(jsonPath("$.activeTransport").value("WIFI"));
    } finally {
      mvc.perform(post("/api/v1/sessions/{id}/cancel", sessionId).with(csrf()).cookie(demo));
    }
  }

  @Test
  void ordinaryUserCannotChangeDeviceConnection() throws Exception {
    Cookie user = login("user@monomates.local", "User123!");
    mvc
      .perform(
        patch("/api/v1/admin/devices/{binId}/connection", UUID.randomUUID())
          .with(csrf())
          .cookie(user)
          .contentType(MediaType.APPLICATION_JSON)
          .content("{\"connectionMode\":\"USB\",\"bridgePort\":8000,\"acceptedDirection\":\"LEFT\",\"swapDirections\":false}")
      )
      .andExpect(status().isForbidden());
  }
}
