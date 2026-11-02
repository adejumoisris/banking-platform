package com.rova.banking;
import org.springframework.context.annotation.*;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.*;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.config.Customizer;
@Configuration @Profile("local")
public class LocalSecurityConfig {
 @Bean SecurityFilterChain local(HttpSecurity http) throws Exception { return SecurityConfig.base(http).httpBasic(Customizer.withDefaults()).build(); }
 @Bean UserDetailsService users() {
  return new InMemoryUserDetailsManager(User.withUsername("demo").password("{noop}local-demo-only").roles("OPERATIONS","COMPLIANCE","TELLER","CREDIT","AUDITOR").build());
 }
}
