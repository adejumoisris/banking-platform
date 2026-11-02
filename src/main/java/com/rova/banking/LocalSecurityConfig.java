package com.rova.banking;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.*;
import org.springframework.core.env.Environment;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.*;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@Profile("local")
public class LocalSecurityConfig {
  @Bean
  SecurityFilterChain local(HttpSecurity http, Environment environment) throws Exception {
    if (environment.matchesProfiles("production")) {
      throw new IllegalStateException(
          "Local authentication cannot be enabled with the production profile");
    }
    return SecurityConfig.base(http).httpBasic(Customizer.withDefaults()).build();
  }

  @Bean
  UserDetailsService users(
      @Value("${banking.local.username:demo}") String username,
      @Value("${banking.local.password}") String password) {
    if (password.length() < 12)
      throw new IllegalArgumentException("Local password requires at least 12 characters");
    return new InMemoryUserDetailsManager(
        User.withUsername(username)
            .password("{bcrypt}" + new BCryptPasswordEncoder().encode(password))
            .roles("OPERATIONS", "COMPLIANCE", "TELLER", "CREDIT", "AUDITOR", "MONITORING")
            .build());
  }
}
