package com.rova.banking.integration;

import com.rova.banking.model.FineractCommand;
import java.util.OptionalLong;

/** Remote provisioning port. Calls must run outside local database transactions. */
public interface FineractClient {
  OptionalLong find(FineractCommand command);

  void validate(FineractCommand command);

  long create(FineractCommand command);
}
