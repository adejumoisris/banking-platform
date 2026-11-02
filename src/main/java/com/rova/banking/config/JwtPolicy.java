package com.rova.banking.config;

import java.util.*;
import org.springframework.security.oauth2.core.*;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.security.oauth2.server.resource.authentication.*;

public final class JwtPolicy {
  private JwtPolicy() {}

  public static OAuth2TokenValidator<Jwt> validator(SecuritySettings settings) {
    return new DelegatingOAuth2TokenValidator<>(
        JwtValidators.createDefaultWithIssuer(settings.issuer().toString()),
        new JwtClaimValidator<Object>("exp", exp -> exp instanceof java.time.Instant),
        new JwtClaimValidator<Object>(
            "aud",
            aud ->
                settings.audience().equals(aud)
                    || aud instanceof Collection<?> values && values.contains(settings.audience())),
        new JwtClaimValidator<Object>(
            "sub", sub -> sub instanceof String value && !value.isBlank()),
        new JwtClaimValidator<Object>(
            "roles",
            roles ->
                roles instanceof Collection<?> values
                    && values.stream().allMatch(String.class::isInstance)));
  }

  public static JwtAuthenticationConverter authenticationConverter() {
    JwtGrantedAuthoritiesConverter roles = new JwtGrantedAuthoritiesConverter();
    roles.setAuthoritiesClaimName("roles");
    roles.setAuthorityPrefix("ROLE_");
    JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
    converter.setJwtGrantedAuthoritiesConverter(roles);
    return converter;
  }
}
