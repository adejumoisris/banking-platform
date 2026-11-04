package com.rova.banking.repository;

import com.rova.banking.model.LedgerEntry;
import java.util.*;

public interface LedgerRepository {
  List<LedgerEntry> findByPosting(UUID postingId, int offset, int limit);

  void append(LedgerEntry debit, LedgerEntry credit);
}
