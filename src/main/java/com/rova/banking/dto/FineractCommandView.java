package com.rova.banking.dto;

import com.rova.banking.model.FineractCommand;
import java.time.Instant;
import java.util.UUID;

public record FineractCommandView(
    UUID id,
    String kind,
    UUID localId,
    String externalId,
    String status,
    int attempts,
    boolean createAttempted,
    Long remoteId,
    String errorCode,
    Instant availableAt) {
  public static FineractCommandView of(FineractCommand command) {
    return new FineractCommandView(
        command.id(),
        command.kind().name(),
        command.localId(),
        command.externalId(),
        command.status().name(),
        command.attempts(),
        command.createAttempted(),
        command.remoteId(),
        command.errorCode(),
        command.availableAt());
  }
}
