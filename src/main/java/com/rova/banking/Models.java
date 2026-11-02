package com.rova.banking;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
public final class Models {
 private Models() {}
 @Entity(name="Customer") @Table(name="customer")
 public static class Customer {
  @Id public UUID id; @Column(name="full_name",length=160) public String fullName;
  @Column(length=254) public String email; @Column(name="kyc_status",length=20) public String kycStatus;
  @Column(name="created_at") public Instant createdAt;
 }
 @Entity(name="BankAccount") @Table(name="bank_account")
 public static class Account {
  @Id public UUID id; @Column(name="customer_id") public UUID customerId;
  @Column(length=3) public String currency; @Column(precision=19,scale=2) public BigDecimal balance;
  @Column(length=20) public String status; @Version public long version;
 }
 @Entity(name="Posting") @Table(name="posting")
 public static class Posting {
  @Id public UUID id; @Column(name="idempotency_key",length=100) public String idempotencyKey;
  @Column(name="source_id") public UUID sourceId; @Column(name="destination_id") public UUID destinationId;
  @Column(precision=19,scale=2) public BigDecimal amount; @Column(length=3) public String currency;
  @Column(length=30) public String kind; @Column(name="created_at") public Instant createdAt;
 }
 @Entity(name="LedgerEntry") @Table(name="ledger_entry")
 public static class LedgerEntry {
  @Id public UUID id; @Column(name="posting_id") public UUID postingId;
  @Column(name="ledger_account",length=80) public String ledgerAccount; @Column(length=6) public String side;
  @Column(precision=19,scale=2) public BigDecimal amount; @Column(length=3) public String currency;
 }
 @Entity(name="Loan") @Table(name="loan")
 public static class Loan {
  @Id public UUID id; @Column(name="account_id") public UUID accountId;
  @Column(precision=19,scale=2) public BigDecimal principal;
  @Column(precision=19,scale=2) public BigDecimal outstanding; @Column(length=20) public String status;
 }
}
