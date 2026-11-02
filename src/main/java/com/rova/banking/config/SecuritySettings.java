package com.rova.banking.config;

import java.net.URI;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("banking.security")
public record SecuritySettings(URI issuer, URI jwkSetUri, String audience) {
  public SecuritySettings {
    requireHttps(issuer, "issuer");
    requireHttps(jwkSetUri, "JWK set URI");
    if (audience == null || audience.isBlank())
      throw new IllegalArgumentException("JWT audience is required");
  }

  private static void requireHttps(URI uri, String name) {
    if (uri == null
        || !"https".equals(uri.getScheme())
        || uri.getHost() == null
        || uri.getUserInfo() != null
        || uri.getFragment() != null) {
      throw new IllegalArgumentException("An explicit HTTPS " + name + " is required");
    }
  }
}
