package com.rova.banking.config;

import com.rova.banking.SecurityConfig;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.*;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@Profile("!local")
@EnableConfigurationProperties(SecuritySettings.class)
public class ProductionSecurityConfig {
  @Bean
  JwtDecoder jwtDecoder(SecuritySettings settings) {
    NimbusJwtDecoder decoder =
        NimbusJwtDecoder.withJwkSetUri(settings.jwkSetUri().toString()).build();
    decoder.setJwtValidator(JwtPolicy.validator(settings));
    return decoder;
  }

  @Bean
  SecurityFilterChain production(HttpSecurity http, SecuritySettings settings) throws Exception {
    return SecurityConfig.base(http)
        .oauth2ResourceServer(
            o -> o.jwt(j -> j.jwtAuthenticationConverter(JwtPolicy.authenticationConverter())))
        .build();
  }
}
