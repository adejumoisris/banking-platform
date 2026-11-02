package com.rova.banking;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
@org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(
    name = "fineract.enabled",
    havingValue = "true")
public class FineractGateway {
  private final RestClient client;

  public FineractGateway(
      @Value("${fineract.url}") String url,
      @Value("${fineract.tenant}") String tenant,
      @Value("${fineract.username}") String user,
      @Value("${fineract.password}") String password) {
    var factory =
        new JdkClientHttpRequestFactory(
            HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build());
    factory.setReadTimeout(Duration.ofSeconds(15));
    client =
        RestClient.builder()
            .baseUrl(url)
            .requestFactory(factory)
            .defaultHeader("Fineract-Platform-TenantId", tenant)
            .defaultHeaders(h -> h.setBasicAuth(user, password))
            .build();
  }

  public String createClient(Map<String, Object> payload) {
    return client.post().uri("/clients").body(payload).retrieve().body(String.class);
  }

  public String client(long id) {
    return client.get().uri("/clients/{id}", id).retrieve().body(String.class);
  }
}
