package com.rova.banking.service;

import com.rova.banking.domain.BusinessException;
import com.rova.banking.domain.NotFound;
import com.rova.banking.export.StatementCsv;
import com.rova.banking.model.AccountStatement;
import com.rova.banking.repository.AccountRepository;
import com.rova.banking.repository.AccountStatementRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ, timeout = 15)
public class AccountStatementService {
  public static final int MAX_CSV_TRANSACTIONS = 10000;
  private static final Instant EARLIEST = Instant.parse("0001-01-01T00:00:00Z");
  private static final Instant LATEST = Instant.parse("9999-12-31T23:59:59.999999Z");
  private final AccountRepository accounts;
  private final AccountStatementRepository statements;
  private final Clock clock;

  public AccountStatementService(
      AccountRepository accounts, AccountStatementRepository statements, Clock clock) {
    this.accounts = accounts;
    this.statements = statements;
    this.clock = clock;
  }

  public AccountStatement history(UUID id, Instant from, Instant to, int page, int size) {
    validatePage(page, size);
    validateRange(from, to, false);
    return read(id, from, to, page, size, false, false);
  }

  public AccountStatement statement(UUID id, Instant from, Instant to, int page, int size) {
    validatePage(page, size);
    validateRange(from, to, true);
    return read(id, from, to, page, size, true, false);
  }

  public String csv(UUID id, Instant from, Instant to) {
    validateRange(from, to, true);
    return StatementCsv.write(read(id, from, to, 0, MAX_CSV_TRANSACTIONS, true, true));
  }

  private AccountStatement read(
      UUID id,
      Instant from,
      Instant to,
      int page,
      int size,
      boolean chronological,
      boolean export) {
    var account = accounts.find(id).orElseThrow(() -> NotFound.resource("Account"));
    var totals = statements.summarize(id, from, to);
    if (export && totals.transactionCount() > MAX_CSV_TRANSACTIONS) {
      throw BusinessException.invalid(
          "CSV exports support at most 10000 transactions; shorten the date range");
    }
    var rows = statements.transactions(id, from, to, page * size, size, chronological);
    return new AccountStatement(
        id, account.getCurrency(), from, to, clock.instant(), totals, page, size, rows);
  }

  private void validatePage(int page, int size) {
    if (page < 0 || page > 100000 || size < 1 || size > 100) {
      throw BusinessException.invalid("Page must be 0..100000 and size 1..100");
    }
  }

  private void validateRange(Instant from, Instant to, boolean required) {
    if (required && (from == null || to == null))
      throw BusinessException.invalid("Both from and to are required");
    if ((from != null && (from.isBefore(EARLIEST) || from.isAfter(LATEST)))
        || (to != null && (to.isBefore(EARLIEST) || to.isAfter(LATEST)))) {
      throw BusinessException.invalid("Date filters must use years 0001 through 9999");
    }
    if (from != null && to != null && !from.isBefore(to)) {
      throw BusinessException.invalid(
          "from must be earlier than to; from is inclusive and to is exclusive");
    }
  }
}
