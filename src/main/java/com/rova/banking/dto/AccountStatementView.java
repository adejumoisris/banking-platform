package com.rova.banking.dto;

import com.rova.banking.model.AccountStatement;
import com.rova.banking.model.AccountTransaction;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record AccountStatementView(
    UUID accountId,
    String currency,
    Instant from,
    Instant to,
    Instant generatedAt,
    BigDecimal openingBalance,
    BigDecimal totalMoneyIn,
    BigDecimal totalMoneyOut,
    BigDecimal closingBalance,
    int page,
    int size,
    long totalElements,
    long totalPages,
    List<AccountTransaction> transactions) {
  public static AccountStatementView of(AccountStatement statement) {
    var totals = statement.totals();
    return new AccountStatementView(
        statement.accountId(),
        statement.currency(),
        statement.from(),
        statement.to(),
        statement.generatedAt(),
        totals.openingBalance(),
        totals.totalMoneyIn(),
        totals.totalMoneyOut(),
        totals.closingBalance(),
        statement.page(),
        statement.size(),
        totals.transactionCount(),
        statement.totalPages(),
        statement.transactions());
  }
}
