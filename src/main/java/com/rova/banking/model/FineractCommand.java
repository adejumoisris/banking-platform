package com.rova.banking.model;

import java.time.Instant;
import java.util.UUID;

public record FineractCommand(
    UUID id,
    String scope,
    Kind kind,
    UUID localId,
    String externalId,
    String payload,
    Status status,
    int attempts,
    boolean createAttempted,
    Long remoteId,
    UUID leaseToken,
    Instant leaseUntil,
    Instant availableAt,
    String errorCode) {
  public enum Kind {
    CUSTOMER,
    ACCOUNT
  }

  public enum Status {
    PENDING,
    PROCESSING,
    RETRY,
    SUCCEEDED,
    REVIEW,
    FAILED
  }

  @Override
  public String toString() {
    return "FineractCommand[id=" + id + ", kind=" + kind + ", status=" + status + "]";
  }
}
