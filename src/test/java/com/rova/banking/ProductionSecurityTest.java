package com.rova.banking;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.rova.banking.config.LocalSecurityConfig;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(
    properties = {
      "banking.security.issuer=https://issuer.example",
      "banking.security.jwk-set-uri=https://issuer.example/jwks",
      "banking.security.audience=banking-api"
    })
@ActiveProfiles("test")
@org.springframework.boot.test.autoconfigure.actuate.observability.AutoConfigureObservability
@AutoConfigureMockMvc
class ProductionSecurityTest {
  @Autowired MockMvc mvc;
  @Autowired ApplicationContext context;
  @MockitoBean JwtDecoder decoder;

  private void token(String role) {
    when(decoder.decode("staff-token"))
        .thenReturn(
            Jwt.withTokenValue("staff-token")
                .header("alg", "RS256")
                .subject("staff-42")
                .issuer("https://issuer.example")
                .audience(List.of("banking-api"))
                .claim("roles", List.of(role))
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(300))
                .build());
  }

  @Test
  void productionDoesNotRegisterDemoAuthentication() throws Exception {
    assertThat(context.getBeansOfType(LocalSecurityConfig.class)).isEmpty();
    mvc.perform(get("/api/v1/accounts/00000000-0000-0000-0000-000000000001"))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void bearerRolesAreEnforcedAndDocsAreDisabled() throws Exception {
    token("AUDITOR");
    mvc.perform(
            post("/api/v1/customers")
                .header("Authorization", "Bearer staff-token")
                .contentType("application/json")
                .content("{}"))
        .andExpect(status().isBadRequest());
    mvc.perform(get("/actuator/prometheus").header("Authorization", "Bearer staff-token"))
        .andExpect(status().isForbidden());
    mvc.perform(get("/v3/api-docs").header("Authorization", "Bearer staff-token"))
        .andExpect(status().isNotFound());
  }

  @Test
  void monitoringRoleCanReadMetrics() throws Exception {
    token("MONITORING");
    mvc.perform(get("/actuator/prometheus").header("Authorization", "Bearer staff-token"))
        .andExpect(status().isOk());
  }

  @Test
  void invalidBearerTokenIsRejected() throws Exception {
    when(decoder.decode("bad-token")).thenThrow(new BadJwtException("Invalid token"));
    mvc.perform(
            get("/api/v1/accounts/00000000-0000-0000-0000-000000000001")
                .header("Authorization", "Bearer bad-token"))
        .andExpect(status().isUnauthorized());
  }
}
