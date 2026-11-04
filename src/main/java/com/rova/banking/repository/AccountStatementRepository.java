package com.rova.banking.repository;

import com.rova.banking.model.AccountStatement.Totals;
import com.rova.banking.model.AccountTransaction;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface AccountStatementRepository {
  Totals summarize(UUID accountId, Instant from, Instant to);

  List<AccountTransaction> transactions(
      UUID accountId, Instant from, Instant to, int offset, int limit, boolean chronological);
}
