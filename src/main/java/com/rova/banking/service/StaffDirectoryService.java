package com.rova.banking.service;

import com.rova.banking.domain.BusinessException;
import com.rova.banking.model.PageResult;
import com.rova.banking.model.StaffDirectory.*;
import com.rova.banking.repository.StaffDirectoryRepository;
import java.time.Clock;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ, timeout = 10)
public class StaffDirectoryService {
  private final StaffDirectoryRepository directory;
  private final Clock clock;

  public StaffDirectoryService(StaffDirectoryRepository directory, Clock clock) {
    this.directory = directory;
    this.clock = clock;
  }

  public PageResult<CustomerRow> customers(String q, String status, int page, int size) {
    validate(page, size);
    filter(status, Set.of("PENDING", "VERIFIED"));
    return directory.customers(query(q), status, page, size);
  }

  public PageResult<AccountRow> accounts(
      String q, UUID customerId, String currency, String status, int page, int size) {
    validate(page, size);
    filter(currency, Set.of("NGN", "USD", "GBP", "EUR"));
    filter(status, Set.of("ACTIVE", "FROZEN", "CLOSED"));
    return directory.accounts(query(q), customerId, currency, status, page, size);
  }

  public PageResult<LoanRow> loans(String q, UUID accountId, String status, int page, int size) {
    validate(page, size);
    filter(status, Set.of("PENDING", "ACTIVE", "CLOSED"));
    return directory.loans(query(q), accountId, status, page, size);
  }

  public Dashboard dashboard() {
    return directory.dashboard(clock.instant());
  }

  private String query(String q) {
    if (q == null) return "";
    if (q.length() > 160)
      throw BusinessException.invalid("Search text must be at most 160 characters");
    return q.strip();
  }

  private void filter(String value, Set<String> allowed) {
    if (value == null || (!value.isEmpty() && !allowed.contains(value)))
      throw BusinessException.invalid("Unsupported directory filter");
  }

  private void validate(int page, int size) {
    if (page < 0 || page > 100000 || size < 1 || size > 100)
      throw BusinessException.invalid("Page must be 0..100000 and size 1..100");
  }
}
