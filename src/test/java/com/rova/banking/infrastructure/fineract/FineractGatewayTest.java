package com.rova.banking.infrastructure.fineract;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rova.banking.config.FineractSettings;
import com.rova.banking.integration.FineractFailure;
import com.rova.banking.model.FineractCommand;
import com.rova.banking.model.FineractCommand.Kind;
import com.rova.banking.model.FineractCommand.Status;
import java.net.URI;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class FineractGatewayTest {
  MockRestServiceServer server;
  FineractGateway gateway;

  @BeforeEach
  void setup() {
    var builder = RestClient.builder();
    server = MockRestServiceServer.bindTo(builder).build();
    gateway =
        new FineractGateway(
            new FineractSettings(
                URI.create("https://fineract.example/api/v1"),
                "sandbox",
                "test",
                "secret",
                1,
                Map.of("NGN", 7L)),
            new ObjectMapper(),
            builder);
  }

  private FineractCommand command(Kind kind) {
    String payload =
        kind == Kind.CUSTOMER
            ? "{\"externalId\":\"stable-id\",\"fullname\":\"Ada\",\"officeId\":1,\"active\":true,\"legalFormId\":1}"
            : "{\"externalId\":\"stable-id\",\"clientId\":42,\"productId\":7,\"_currency\":\"NGN\"}";
    return new FineractCommand(
        UUID.randomUUID(),
        "scope",
        kind,
        UUID.randomUUID(),
        "stable-id",
        payload,
        Status.PROCESSING,
        1,
        false,
        null,
        UUID.randomUUID(),
        Instant.now().plusSeconds(90),
        Instant.now(),
        null);
  }

  @Test
  void clientLookupUsesExternalIdAndTenantAuthentication() {
    server
        .expect(requestTo("https://fineract.example/api/v1/clients/external-id/stable-id"))
        .andExpect(header("Fineract-Platform-TenantId", "sandbox"))
        .andExpect(header("Authorization", "Basic dGVzdDpzZWNyZXQ="))
        .andRespond(
            withSuccess(
                "{\"id\":42,\"externalId\":\"stable-id\",\"officeId\":1,\"active\":true,\"legalFormId\":1}",
                MediaType.APPLICATION_JSON));
    assertThat(gateway.find(command(Kind.CUSTOMER))).hasValue(42);
    server.verify();
  }

  @Test
  void only404MeansMissingAndAuthenticationFailureIsPermanent() {
    String url = "https://fineract.example/api/v1/clients/external-id/stable-id";
    server.expect(requestTo(url)).andRespond(withResourceNotFound());
    server.expect(requestTo(url)).andRespond(withStatus(HttpStatus.UNAUTHORIZED));
    assertThat(gateway.find(command(Kind.CUSTOMER))).isEmpty();
    assertThatThrownBy(() -> gateway.find(command(Kind.CUSTOMER)))
        .isInstanceOfSatisfying(
            FineractFailure.class,
            error -> {
              assertThat(error.code()).isEqualTo("HTTP_401");
              assertThat(error.retryable()).isFalse();
            });
    server.verify();
  }

  @Test
  void wrongRemoteIdentityIsNeverMapped() {
    server
        .expect(anything())
        .andRespond(
            withSuccess(
                "{\"id\":42,\"externalId\":\"someone-else\",\"officeId\":1,\"active\":true,\"legalFormId\":1}",
                MediaType.APPLICATION_JSON));
    assertThatThrownBy(() -> gateway.find(command(Kind.CUSTOMER)))
        .hasMessage("REMOTE_IDENTITY_MISMATCH");
    server.verify();
  }

  @Test
  void savingsChecksProductCurrencyAndDoesNotSendInternalMetadata() {
    server
        .expect(requestTo("https://fineract.example/api/v1/savingsproducts/7"))
        .andRespond(withSuccess("{\"currency\":{\"code\":\"NGN\"}}", MediaType.APPLICATION_JSON));
    server
        .expect(requestTo("https://fineract.example/api/v1/savingsaccounts"))
        .andExpect(method(HttpMethod.POST))
        .andExpect(jsonPath("$.clientId").value(42))
        .andExpect(jsonPath("$.productId").value(7))
        .andExpect(jsonPath("$.externalId").value("stable-id"))
        .andExpect(jsonPath("$._currency").doesNotExist())
        .andRespond(withSuccess("{\"resourceId\":99}", MediaType.APPLICATION_JSON));
    gateway.validate(command(Kind.ACCOUNT));
    assertThat(gateway.create(command(Kind.ACCOUNT))).isEqualTo(99);
    server.verify();
  }

  @Test
  void productCurrencyMismatchStopsBeforeCreate() {
    server
        .expect(anything())
        .andRespond(withSuccess("{\"currency\":{\"code\":\"USD\"}}", MediaType.APPLICATION_JSON));
    assertThatThrownBy(() -> gateway.validate(command(Kind.ACCOUNT)))
        .hasMessage("PRODUCT_CURRENCY_MISMATCH");
    server.verify();
  }

  @Test
  void savingsLookupChecksClientProductAndCurrency() {
    server
        .expect(requestTo("https://fineract.example/api/v1/savingsaccounts/external-id/stable-id"))
        .andRespond(
            withSuccess(
                "{\"id\":99,\"externalId\":\"stable-id\",\"clientId\":42,\"savingsProductId\":7,\"currency\":{\"code\":\"NGN\"}}",
                MediaType.APPLICATION_JSON));
    assertThat(gateway.find(command(Kind.ACCOUNT))).hasValue(99);
    server.verify();
  }

  @Test
  void settingsRejectRemotePlainHttpAndSeparateTenants() {
    assertThatThrownBy(
            () ->
                new FineractSettings(
                    URI.create("http://remote.example/api/v1"), "a", "test", "secret", 1, Map.of()))
        .isInstanceOf(IllegalArgumentException.class);
    var first =
        new FineractSettings(
            URI.create("https://remote.example/api/v1"), "a", "test", "secret", 1, Map.of());
    var second =
        new FineractSettings(
            URI.create("https://remote.example/api/v1"), "b", "test", "secret", 1, Map.of());
    assertThat(first.scope()).isNotEqualTo(second.scope());
    assertThat(first.toString()).doesNotContain("secret");
  }

  @Test
  void fractionalRemoteIdsAreRejectedAsAmbiguousResponses() {
    server
        .expect(anything())
        .andRespond(withSuccess("{\"resourceId\":42.5}", MediaType.APPLICATION_JSON));
    assertThatThrownBy(() -> gateway.create(command(Kind.CUSTOMER)))
        .isInstanceOfSatisfying(
            FineractFailure.class,
            error -> {
              assertThat(error.code()).isEqualTo("INVALID_CREATE_RESPONSE");
              assertThat(error.retryable()).isTrue();
            });
    server.verify();
  }

  @Test
  void environmentStyleCurrencyKeysAreNormalizedAndZeroMeansUnconfigured() {
    var settings =
        new FineractSettings(
            URI.create("https://remote.example/api/v1"),
            "a",
            "test",
            "secret",
            1,
            Map.of("ngn", 7L, "usd", 0L));
    assertThat(settings.savingsProducts()).containsExactlyEntriesOf(Map.of("NGN", 7L));
  }

  @Test
  void clientCreationIncludesRequiredPersonLegalForm() {
    server
        .expect(requestTo("https://fineract.example/api/v1/clients"))
        .andExpect(method(HttpMethod.POST))
        .andExpect(jsonPath("$.legalFormId").value(1))
        .andExpect(jsonPath("$.fullname").value("Ada"))
        .andRespond(withSuccess("{\"resourceId\":42}", MediaType.APPLICATION_JSON));
    assertThat(gateway.create(command(Kind.CUSTOMER))).isEqualTo(42);
    server.verify();
  }
}
