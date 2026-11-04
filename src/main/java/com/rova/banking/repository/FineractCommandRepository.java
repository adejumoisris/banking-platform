package com.rova.banking.repository;

import com.rova.banking.model.FineractCommand;
import com.rova.banking.model.FineractCommand.Kind;
import com.rova.banking.model.FineractCommand.Status;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FineractCommandRepository {
  Optional<FineractCommand> find(String scope, UUID id);

  Optional<FineractCommand> find(String scope, Kind kind, UUID localId);

  void insert(FineractCommand command, Instant now);

  List<UUID> ready(String scope, Instant now);

  Optional<FineractCommand> claim(String scope, UUID id, Instant now);

  boolean prepareCreate(FineractCommand command, Instant now);

  boolean finish(
      FineractCommand command,
      Status status,
      Long remoteId,
      String error,
      Instant next,
      Instant now);

  boolean reconcile(String scope, UUID id, Instant now);
}
