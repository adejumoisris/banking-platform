package com.rova.banking;

import static org.assertj.core.api.Assertions.*;

import com.nimbusds.jose.jwk.*;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.rova.banking.config.*;
import java.net.URI;
import java.security.*;
import java.security.interfaces.*;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.*;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.*;

class JwtPolicyTest {
  static final SecuritySettings SETTINGS =
      new SecuritySettings(
          URI.create("https://issuer.example"),
          URI.create("https://issuer.example/jwks"),
          "banking-api");
  static KeyPair keys;
  static NimbusJwtDecoder decoder;

  @BeforeAll
  static void setup() throws Exception {
    KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
    generator.initialize(2048);
    keys = generator.generateKeyPair();
    decoder = NimbusJwtDecoder.withPublicKey((RSAPublicKey) keys.getPublic()).build();
    decoder.setJwtValidator(JwtPolicy.validator(SETTINGS));
  }

  private String token(
      String issuer, String audience, Object roles, Instant expires, KeyPair signingKeys) {
    var rsa =
        new com.nimbusds.jose.jwk.RSAKey.Builder((RSAPublicKey) signingKeys.getPublic())
            .privateKey((RSAPrivateKey) signingKeys.getPrivate())
            .build();
    var encoder = new NimbusJwtEncoder(new ImmutableJWKSet<>(new JWKSet(rsa)));
    var claims =
        JwtClaimsSet.builder()
            .issuer(issuer)
            .subject("staff-1")
            .audience(List.of(audience))
            .issuedAt(Instant.now().minusSeconds(600))
            .expiresAt(expires)
            .claim("roles", roles)
            .build();
    return encoder
        .encode(JwtEncoderParameters.from(JwsHeader.with(SignatureAlgorithm.RS256).build(), claims))
        .getTokenValue();
  }

  @Test
  void acceptsValidSignedTokenAndMapsStaffRole() {
    var jwt =
        decoder.decode(
            token(
                "https://issuer.example",
                "banking-api",
                List.of("TELLER"),
                Instant.now().plusSeconds(300),
                keys));
    assertThat(JwtPolicy.authenticationConverter().convert(jwt).getAuthorities())
        .extracting("authority")
        .containsExactly("ROLE_TELLER");
  }

  @Test
  void rejectsWrongAudienceAndIssuer() {
    assertThatThrownBy(
            () ->
                decoder.decode(
                    token(
                        "https://issuer.example",
                        "other-service",
                        List.of("TELLER"),
                        Instant.now().plusSeconds(300),
                        keys)))
        .isInstanceOf(JwtException.class);
    assertThatThrownBy(
            () ->
                decoder.decode(
                    token(
                        "https://attacker.example",
                        "banking-api",
                        List.of("TELLER"),
                        Instant.now().plusSeconds(300),
                        keys)))
        .isInstanceOf(JwtException.class);
  }

  @Test
  void rejectsExpiredAndMalformedRoles() {
    assertThatThrownBy(
            () ->
                decoder.decode(
                    token(
                        "https://issuer.example",
                        "banking-api",
                        List.of("TELLER"),
                        Instant.now().minusSeconds(120),
                        keys)))
        .isInstanceOf(JwtException.class);
    assertThatThrownBy(
            () ->
                decoder.decode(
                    token(
                        "https://issuer.example",
                        "banking-api",
                        "TELLER",
                        Instant.now().plusSeconds(300),
                        keys)))
        .isInstanceOf(JwtException.class);
  }

  @Test
  void rejectsUntrustedSignature() throws Exception {
    KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
    generator.initialize(2048);
    var untrusted = generator.generateKeyPair();
    assertThatThrownBy(
            () ->
                decoder.decode(
                    token(
                        "https://issuer.example",
                        "banking-api",
                        List.of("TELLER"),
                        Instant.now().plusSeconds(300),
                        untrusted)))
        .isInstanceOf(JwtException.class);
  }

  @Test
  void requiresExplicitHttpsIdentityConfiguration() {
    assertThatThrownBy(
            () ->
                new SecuritySettings(
                    URI.create("http://localhost"),
                    URI.create("https://issuer.example/jwks"),
                    "api"))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new SecuritySettings(SETTINGS.issuer(), SETTINGS.jwkSetUri(), ""))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void requiresAnExpirationClaim() {
    Jwt token =
        Jwt.withTokenValue("missing-expiration")
            .header("alg", "RS256")
            .issuer("https://issuer.example")
            .subject("staff-1")
            .audience(List.of("banking-api"))
            .claim("roles", List.of("AUDITOR"))
            .build();
    assertThat(JwtPolicy.validator(SETTINGS).validate(token).hasErrors()).isTrue();
  }
}
