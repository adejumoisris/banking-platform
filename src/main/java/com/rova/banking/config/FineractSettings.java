package com.rova.banking.config;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("fineract")
public record FineractSettings(
    URI url,
    String tenant,
    String username,
    String password,
    long officeId,
    Map<String, Long> savingsProducts) {
  public FineractSettings {
    if (url == null
        || url.getHost() == null
        || url.getUserInfo() != null
        || url.getQuery() != null
        || url.getFragment() != null
        || !("https".equals(url.getScheme())
            || ("http".equals(url.getScheme())
                && ("127.0.0.1".equals(url.getHost()) || "localhost".equals(url.getHost()))))) {
      throw new IllegalArgumentException(
          "Fineract requires HTTPS (HTTP is allowed only on loopback)");
    }
    if (tenant == null
        || !tenant.matches("[A-Za-z0-9_-]{1,100}")
        || username == null
        || username.isBlank()
        || password == null
        || password.isBlank()
        || officeId < 1) {
      throw new IllegalArgumentException(
          "Fineract tenant, credentials and positive office ID are required");
    }
    Map<String, Long> configured = new java.util.HashMap<>();
    if (savingsProducts != null) {
      for (var entry : savingsProducts.entrySet()) {
        if (entry.getValue() == null || entry.getValue() < 0)
          throw new IllegalArgumentException("Invalid Fineract savings product ID");
        if (entry.getValue() > 0)
          configured.put(entry.getKey().toUpperCase(java.util.Locale.ROOT), entry.getValue());
      }
    }
    savingsProducts = Map.copyOf(configured);
    for (var product : savingsProducts.entrySet()) {
      if (!product.getKey().matches("NGN|USD|GBP|EUR")
          || product.getValue() == null
          || product.getValue() < 1) {
        throw new IllegalArgumentException(
            "Fineract savings products require supported currencies and positive IDs");
      }
    }
  }

  public String scope() {
    try {
      return HexFormat.of()
          .formatHex(
              MessageDigest.getInstance("SHA-256")
                  .digest(
                      (url.toString().replaceAll("/+$", "") + "|" + tenant)
                          .getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException ex) {
      throw new IllegalStateException(ex);
    }
  }

  @Override
  public String toString() {
    return "FineractSettings[tenant=" + tenant + ", credentials=REDACTED]";
  }
}
