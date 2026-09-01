package com.monomates.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

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
 * M1 test debt (flagged since CX-002/CX-006/CX-007/CX-011 and never paid
 * off): register, login, logout, /me, 401, 403 and CSRF, exercised through
 * the real Spring Security filter chain against real PostgreSQL.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@ActiveProfiles("local")
class AuthFlowTest {

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
    jdbc.update("delete from app_users where email = ?", registeredEmail);
    registeredEmail = null;
  }

  private String freshEmail() {
    String email = "it-auth-" + UUID.randomUUID() + "@monomates.test";
    registeredEmail = email;
    return email;
  }

  @Test
  void registerCreatesAnActiveUserAndSetsTheAuthCookie() throws Exception {
    String email = freshEmail();
    MvcResult result = mvc
      .perform(
        post("/api/v1/auth/register")
          .with(csrf())
          .contentType(MediaType.APPLICATION_JSON)
          .content("{\"fullName\":\"Auth Test\",\"email\":\"%s\",\"password\":\"TestPassword123!\"}".formatted(email))
      )
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.user.email").value(email.toLowerCase()))
      .andExpect(jsonPath("$.user.role").value("USER"))
      .andExpect(jsonPath("$.user.status").value("ACTIVE"))
      .andReturn();
    assertThat(result.getResponse().getCookie("mm_access_token")).isNotNull();
  }

  @Test
  void registeringTheSameEmailTwiceIsRejected() throws Exception {
    String email = freshEmail();
    String body = "{\"fullName\":\"Auth Test\",\"email\":\"%s\",\"password\":\"TestPassword123!\"}".formatted(email);
    mvc
      .perform(post("/api/v1/auth/register").with(csrf()).contentType(MediaType.APPLICATION_JSON).content(body))
      .andExpect(status().isOk());
    mvc
      .perform(post("/api/v1/auth/register").with(csrf()).contentType(MediaType.APPLICATION_JSON).content(body))
      .andExpect(status().isConflict());
  }

  @Test
  void loginWithWrongPasswordIsRejectedWithoutRevealingWhichFieldWasWrong() throws Exception {
    String email = freshEmail();
    mvc
      .perform(
        post("/api/v1/auth/register")
          .with(csrf())
          .contentType(MediaType.APPLICATION_JSON)
          .content("{\"fullName\":\"Auth Test\",\"email\":\"%s\",\"password\":\"TestPassword123!\"}".formatted(email))
      )
      .andExpect(status().isOk());

    mvc
      .perform(
        post("/api/v1/auth/login")
          .with(csrf())
          .contentType(MediaType.APPLICATION_JSON)
          .content("{\"email\":\"%s\",\"password\":\"WrongPassword!\"}".formatted(email))
      )
      .andExpect(status().isUnprocessableEntity())
      .andExpect(jsonPath("$.message").value("Email or password is incorrect."));
  }

  @Test
  void meWithoutACookieIsUnauthorized() throws Exception {
    mvc.perform(get("/api/v1/auth/me")).andExpect(status().isUnauthorized());
  }

  @Test
  void meWithAValidCookieReturnsTheAuthenticatedUser() throws Exception {
    String email = freshEmail();
    MvcResult register = mvc
      .perform(
        post("/api/v1/auth/register")
          .with(csrf())
          .contentType(MediaType.APPLICATION_JSON)
          .content("{\"fullName\":\"Auth Test\",\"email\":\"%s\",\"password\":\"TestPassword123!\"}".formatted(email))
      )
      .andExpect(status().isOk())
      .andReturn();
    Cookie auth = register.getResponse().getCookie("mm_access_token");

    mvc
      .perform(get("/api/v1/auth/me").cookie(auth))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.email").value(email.toLowerCase()));
  }

  @Test
  void logoutClearsTheCookieSoMeBecomesUnauthorized() throws Exception {
    String email = freshEmail();
    MvcResult register = mvc
      .perform(
        post("/api/v1/auth/register")
          .with(csrf())
          .contentType(MediaType.APPLICATION_JSON)
          .content("{\"fullName\":\"Auth Test\",\"email\":\"%s\",\"password\":\"TestPassword123!\"}".formatted(email))
      )
      .andExpect(status().isOk())
      .andReturn();
    Cookie auth = register.getResponse().getCookie("mm_access_token");

    MvcResult logout = mvc
      .perform(post("/api/v1/auth/logout").with(csrf()).cookie(auth))
      .andExpect(status().isNoContent())
      .andReturn();
    Cookie cleared = logout.getResponse().getCookie("mm_access_token");
    assertThat(cleared).isNotNull();
    assertThat(cleared.getMaxAge()).isEqualTo(0);
  }

  @Test
  void nonAdminIsForbiddenFromAdminEndpoints() throws Exception {
    String email = freshEmail();
    MvcResult register = mvc
      .perform(
        post("/api/v1/auth/register")
          .with(csrf())
          .contentType(MediaType.APPLICATION_JSON)
          .content("{\"fullName\":\"Auth Test\",\"email\":\"%s\",\"password\":\"TestPassword123!\"}".formatted(email))
      )
      .andExpect(status().isOk())
      .andReturn();
    Cookie auth = register.getResponse().getCookie("mm_access_token");

    mvc
      .perform(get("/api/v1/admin/bins").cookie(auth))
      .andExpect(status().isForbidden());
  }

  @Test
  void adminCanAccessAdminEndpoints() throws Exception {
    MvcResult login = mvc
      .perform(
        post("/api/v1/auth/login")
          .with(csrf())
          .contentType(MediaType.APPLICATION_JSON)
          .content("{\"email\":\"admin@monomates.local\",\"password\":\"Admin123!\"}")
      )
      .andExpect(status().isOk())
      .andReturn();
    Cookie auth = login.getResponse().getCookie("mm_access_token");

    mvc.perform(get("/api/v1/admin/bins").cookie(auth)).andExpect(status().isOk());
  }

  @Test
  void aMutatingRequestWithoutACsrfTokenIsRejected() throws Exception {
    String email = "it-auth-nocsrf-" + UUID.randomUUID() + "@monomates.test";
    mvc
      .perform(
        post("/api/v1/auth/register")
          .contentType(MediaType.APPLICATION_JSON)
          .content("{\"fullName\":\"Auth Test\",\"email\":\"%s\",\"password\":\"TestPassword123!\"}".formatted(email))
      )
      .andExpect(status().isForbidden());
  }

  @Test
  void theCsrfEndpointIssuesAToken() throws Exception {
    // The live server also sets the XSRF-TOKEN cookie here (verified
    // manually via curl against the running container); MockMvc's simulated
    // response does not reliably surface CookieCsrfTokenRepository's
    // deferred cookie write, so this test only asserts the token itself,
    // which is what the frontend actually reads from the JSON body.
    mvc
      .perform(get("/api/v1/auth/csrf"))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.token").isNotEmpty());
  }

  @Test
  void configuredFrontendOriginReceivesCorsHeaders() throws Exception {
    mvc
      .perform(
        get("/api/v1/bins")
          .header("Origin", "http://localhost:5173")
      )
      .andExpect(status().isOk())
      .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:5173"));
  }

  @Test
  void aSuspendedAccountIsRejectedOnTheNextAuthenticatedRequestEvenWithAStillValidCookie() throws Exception {
    String email = freshEmail();
    MvcResult register = mvc
      .perform(
        post("/api/v1/auth/register")
          .with(csrf())
          .contentType(MediaType.APPLICATION_JSON)
          .content("{\"fullName\":\"Auth Test\",\"email\":\"%s\",\"password\":\"TestPassword123!\"}".formatted(email))
      )
      .andExpect(status().isOk())
      .andReturn();
    Cookie auth = register.getResponse().getCookie("mm_access_token");

    mvc.perform(get("/api/v1/auth/me").cookie(auth)).andExpect(status().isOk());

    jdbc.update("update app_users set status = 'SUSPENDED' where email = ?", email);

    mvc
      .perform(get("/api/v1/auth/me").cookie(auth))
      .andExpect(status().isUnauthorized());
  }

  @Test
  void authenticatedUserCanUpdateAndReloadTheirProfileName() throws Exception {
    String email = freshEmail();
    MvcResult register = mvc
      .perform(
        post("/api/v1/auth/register")
          .with(csrf())
          .contentType(MediaType.APPLICATION_JSON)
          .content("{\"fullName\":\"Old Name\",\"email\":\"%s\",\"password\":\"TestPassword123!\"}".formatted(email))
      )
      .andExpect(status().isOk())
      .andReturn();
    Cookie auth = register.getResponse().getCookie("mm_access_token");

    mvc
      .perform(
        patch("/api/v1/users/me")
          .with(csrf())
          .cookie(auth)
          .contentType(MediaType.APPLICATION_JSON)
          .content("{\"fullName\":\"Updated Name\"}")
      )
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.fullName").value("Updated Name"));

    mvc
      .perform(get("/api/v1/users/me").cookie(auth))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.fullName").value("Updated Name"));
  }
}
