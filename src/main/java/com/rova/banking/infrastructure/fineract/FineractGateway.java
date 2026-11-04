package com.rova.banking.infrastructure.fineract;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rova.banking.config.FineractSettings;
import com.rova.banking.integration.FineractClient;
import com.rova.banking.integration.FineractFailure;
import com.rova.banking.model.FineractCommand;
import com.rova.banking.model.FineractCommand.Kind;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.OptionalLong;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

@Component
@ConditionalOnProperty(name = "fineract.enabled", havingValue = "true")
public class FineractGateway implements FineractClient {
  private final RestClient client;
  private final ObjectMapper json;

  @org.springframework.beans.factory.annotation.Autowired
  public FineractGateway(FineractSettings settings, ObjectMapper json) {
    this(settings, json, RestClient.builder().requestFactory(factory()));
  }

  FineractGateway(FineractSettings settings, ObjectMapper json, RestClient.Builder builder) {
    this.json = json;
    client =
        builder
            .baseUrl(settings.url().toString().replaceAll("/+$", ""))
            .defaultHeader("Fineract-Platform-TenantId", settings.tenant())
            .defaultHeaders(
                headers -> headers.setBasicAuth(settings.username(), settings.password()))
            .build();
  }

  private static JdkClientHttpRequestFactory factory() {
    var factory =
        new JdkClientHttpRequestFactory(
            HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build());
    factory.setReadTimeout(Duration.ofSeconds(15));
    return factory;
  }

  private String resource(FineractCommand command) {
    return command.kind() == Kind.CUSTOMER ? "/clients" : "/savingsaccounts";
  }

  private JsonNode payload(FineractCommand command) {
    try {
      return json.readTree(command.payload());
    } catch (Exception ex) {
      throw new FineractFailure("INVALID_PAYLOAD", false);
    }
  }

  private JsonNode get(String path, Object... values) {
    try {
      JsonNode body =
          client
              .get()
              .uri(path, values)
              .accept(MediaType.APPLICATION_JSON)
              .retrieve()
              .body(JsonNode.class);
      if (body == null || !body.isObject()) throw new FineractFailure("INVALID_RESPONSE", true);
      return body;
    } catch (RestClientResponseException ex) {
      throw failure(ex);
    } catch (ResourceAccessException ex) {
      throw new FineractFailure("TRANSPORT", true);
    }
  }

  public OptionalLong find(FineractCommand command) {
    JsonNode remote;
    try {
      remote = get(resource(command) + "/external-id/{externalId}", command.externalId());
    } catch (FineractFailure ex) {
      if ("HTTP_404".equals(ex.code())) return OptionalLong.empty();
      throw ex;
    }
    JsonNode expected = payload(command);
    boolean matches =
        command.externalId().equals(remote.path("externalId").asText())
            && remote.path("id").isIntegralNumber()
            && remote.path("id").canConvertToLong()
            && remote.path("id").asLong() > 0;
    if (command.kind() == Kind.CUSTOMER) {
      matches &=
          remote.path("officeId").asLong(-1) == expected.path("officeId").asLong()
              && remote.path("active").asBoolean();
    } else {
      matches &=
          remote.path("clientId").asLong(-1) == expected.path("clientId").asLong()
              && remote.path("savingsProductId").asLong(-1) == expected.path("productId").asLong()
              && remote
                  .path("currency")
                  .path("code")
                  .asText()
                  .equals(expected.path("_currency").asText());
    }
    if (!matches) throw new FineractFailure("REMOTE_IDENTITY_MISMATCH", false);
    return OptionalLong.of(remote.path("id").asLong());
  }

  public void validate(FineractCommand command) {
    if (command.kind() == Kind.ACCOUNT) {
      JsonNode expected = payload(command);
      JsonNode product = get("/savingsproducts/{id}", expected.path("productId").asLong());
      if (!expected
          .path("_currency")
          .asText()
          .equals(product.path("currency").path("code").asText())) {
        throw new FineractFailure("PRODUCT_CURRENCY_MISMATCH", false);
      }
    }
  }

  public long create(FineractCommand command) {
    try {
      var request = payload(command).deepCopy();
      ((com.fasterxml.jackson.databind.node.ObjectNode) request).remove("_currency");
      JsonNode result =
          client
              .post()
              .uri(resource(command))
              .contentType(MediaType.APPLICATION_JSON)
              .accept(MediaType.APPLICATION_JSON)
              .body(request)
              .retrieve()
              .body(JsonNode.class);
      if (result == null
          || !result.path("resourceId").isIntegralNumber()
          || !result.path("resourceId").canConvertToLong()
          || result.path("resourceId").asLong() < 1) {
        throw new FineractFailure("INVALID_CREATE_RESPONSE", true);
      }
      return result.path("resourceId").asLong();
    } catch (RestClientResponseException ex) {
      throw failure(ex);
    } catch (ResourceAccessException ex) {
      throw new FineractFailure("TRANSPORT", true);
    }
  }

  private FineractFailure failure(RestClientResponseException ex) {
    int status = ex.getStatusCode().value();
    return new FineractFailure(
        "HTTP_" + status, status >= 500 || status == 408 || status == 409 || status == 429);
  }
}
