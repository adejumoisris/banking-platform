package com.rova.banking.dto;

import com.rova.banking.model.AccountStatement;
import com.rova.banking.model.AccountTransaction;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record TransactionHistoryView(
    UUID accountId,
    String currency,
    Instant from,
    Instant to,
    Instant generatedAt,
    int page,
    int size,
    long totalElements,
    long totalPages,
    List<AccountTransaction> transactions) {
  public static TransactionHistoryView of(AccountStatement statement) {
    return new TransactionHistoryView(
        statement.accountId(),
        statement.currency(),
        statement.from(),
        statement.to(),
        statement.generatedAt(),
        statement.page(),
        statement.size(),
        statement.totals().transactionCount(),
        statement.totalPages(),
        statement.transactions());
  }
}
