package com.rova.banking.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record AccountStatement(
    UUID accountId,
    String currency,
    Instant from,
    Instant to,
    Instant generatedAt,
    Totals totals,
    int page,
    int size,
    List<AccountTransaction> transactions) {
  public AccountStatement {
    transactions = List.copyOf(transactions);
  }

  public long totalPages() {
    return totals.transactionCount() / size + (totals.transactionCount() % size == 0 ? 0 : 1);
  }

  public record Totals(
      BigDecimal openingBalance,
      BigDecimal totalMoneyIn,
      BigDecimal totalMoneyOut,
      long transactionCount) {
    public BigDecimal closingBalance() {
      return openingBalance.add(totalMoneyIn).subtract(totalMoneyOut);
    }
  }
}
