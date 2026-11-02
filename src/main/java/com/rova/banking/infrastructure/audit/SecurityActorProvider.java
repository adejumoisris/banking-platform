package com.rova.banking.infrastructure.audit;

import com.rova.banking.audit.ActorProvider;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Component
public class SecurityActorProvider implements ActorProvider {
  @Override
  public String currentActor() {
    var authentication = SecurityContextHolder.getContext().getAuthentication();
    return authentication != null && authentication.isAuthenticated()
        ? authentication.getName()
        : "system";
  }
}
