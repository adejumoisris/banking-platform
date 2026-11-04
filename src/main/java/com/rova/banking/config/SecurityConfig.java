package com.rova.banking.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {
  public static HttpSecurity base(HttpSecurity http) throws Exception {
    return http.csrf(csrf -> csrf.disable())
        .sessionManagement(
            session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .authorizeHttpRequests(
            auth ->
                auth.requestMatchers("/", "/index.html", "/assets/**", "/api/v1/ui/config")
                    .permitAll()
                    .requestMatchers("/actuator/health", "/actuator/health/**")
                    .permitAll()
                    .requestMatchers("/actuator/prometheus")
                    .hasRole("MONITORING")
                    .requestMatchers("/actuator/**")
                    .denyAll()
                    .anyRequest()
                    .authenticated());
  }
}
