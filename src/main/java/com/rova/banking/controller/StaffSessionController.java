package com.rova.banking.controller;

import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
public class StaffSessionController {
  public record UiConfig(String name, boolean basicAuth, boolean fineractEnabled) {}

  public record StaffSession(String username, List<String> roles) {}

  private final Environment environment;
  private final boolean fineractEnabled;

  public StaffSessionController(
      Environment environment, @Value("${fineract.enabled:false}") boolean fineractEnabled) {
    this.environment = environment;
    this.fineractEnabled = fineractEnabled;
  }

  @GetMapping("/ui/config")
  public ResponseEntity<UiConfig> config() {
    return ResponseEntity.ok()
        .cacheControl(CacheControl.noStore())
        .body(new UiConfig("Rova", environment.matchesProfiles("local"), fineractEnabled));
  }

  @GetMapping("/session")
  public ResponseEntity<StaffSession> session(Authentication user) {
    var roles =
        user.getAuthorities().stream()
            .map(GrantedAuthority::getAuthority)
            .filter(value -> value.startsWith("ROLE_"))
            .map(value -> value.substring(5))
            .sorted()
            .toList();
    return ResponseEntity.ok()
        .cacheControl(CacheControl.noStore())
        .body(new StaffSession(user.getName(), roles));
  }
}
