package com.rova.banking.infrastructure.fineract;

import com.rova.banking.service.FineractService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(
    name = {"fineract.enabled", "fineract.worker-enabled"},
    havingValue = "true")
public class FineractDispatcher {
  private static final Logger LOG = LoggerFactory.getLogger(FineractDispatcher.class);
  private final FineractService service;

  public FineractDispatcher(FineractService service) {
    this.service = service;
  }

  @Scheduled(fixedDelayString = "${fineract.poll-delay-ms:5000}")
  public void dispatch() {
    try {
      service.dispatchReady();
    } catch (RuntimeException ex) {
      LOG.error("Fineract dispatcher failed: {}", ex.getClass().getSimpleName());
    }
  }
}
