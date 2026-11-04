package com.rova.banking.dto;

import com.rova.banking.model.Account;
import com.rova.banking.model.Customer;
import com.rova.banking.model.LedgerEntry;
import com.rova.banking.model.Loan;
import com.rova.banking.model.Posting;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public final class Views {
  private Views() {}

  public record CustomerView(
      UUID id, String fullName, String email, String kycStatus, Instant createdAt) {
    public static CustomerView of(Customer c) {
      return new CustomerView(
          c.getId(), c.getFullName(), c.getEmail(), c.getKycStatus().name(), c.getCreatedAt());
    }
  }

  public record AccountView(
      UUID id, UUID customerId, String currency, BigDecimal balance, String status, long version) {
    public static AccountView of(Account a) {
      return new AccountView(
          a.getId(),
          a.getCustomerId(),
          a.getCurrency(),
          a.getBalance(),
          a.getStatus().name(),
          a.getVersion());
    }
  }

  public record PostingView(
      UUID id,
      UUID sourceId,
      UUID destinationId,
      BigDecimal amount,
      String currency,
      String kind,
      Instant createdAt) {
    public static PostingView of(Posting p) {
      return new PostingView(
          p.getId(),
          p.getSourceId(),
          p.getDestinationId(),
          p.getAmount(),
          p.getCurrency(),
          p.getKind().name(),
          p.getCreatedAt());
    }
  }

  public record LoanView(
      UUID id, UUID accountId, BigDecimal principal, BigDecimal outstanding, String status) {
    public static LoanView of(Loan l) {
      return new LoanView(
          l.getId(), l.getAccountId(), l.getPrincipal(), l.getOutstanding(), l.getStatus().name());
    }
  }

  public record EntryView(
      UUID id,
      UUID postingId,
      String ledgerAccount,
      String side,
      BigDecimal amount,
      String currency) {
    public static EntryView of(LedgerEntry e) {
      return new EntryView(
          e.getId(),
          e.getPostingId(),
          e.getLedgerAccount(),
          e.getSide().name(),
          e.getAmount(),
          e.getCurrency());
    }
  }
}
