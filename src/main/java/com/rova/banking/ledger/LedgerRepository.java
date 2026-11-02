package com.rova.banking.ledger;

import java.util.*;

public interface LedgerRepository {
  List<LedgerEntry> findByPosting(UUID postingId, int offset, int limit);

  void append(LedgerEntry debit, LedgerEntry credit);
}
