package com.rova.banking.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public final class StaffDirectory {
  private StaffDirectory() {}

  public record CustomerRow(
      UUID id,
      String fullName,
      String email,
      String kycStatus,
      Instant createdAt,
      long accountCount) {}

  public record AccountRow(
      UUID id,
      UUID customerId,
      String customerName,
      String currency,
      BigDecimal balance,
      String status) {}

  public record LoanRow(
      UUID id,
      UUID accountId,
      UUID customerId,
      String customerName,
      String currency,
      BigDecimal principal,
      BigDecimal outstanding,
      String status) {}

  public record CurrencyBalance(String currency, BigDecimal balance, long accounts) {}

  public record ActivityDay(LocalDate date, long count) {}

  public record PostingRow(
      UUID id,
      String kind,
      BigDecimal amount,
      String currency,
      Instant createdAt,
      UUID sourceId,
      UUID destinationId,
      String sourceName,
      String destinationName) {}

  public record Dashboard(
      long customers,
      long verifiedCustomers,
      long pendingCustomers,
      long accounts,
      long activeAccounts,
      long loans,
      long activeLoans,
      long transactionsToday,
      List<CurrencyBalance> balances,
      List<ActivityDay> activity,
      List<PostingRow> recentTransactions,
      Instant generatedAt) {}
}
