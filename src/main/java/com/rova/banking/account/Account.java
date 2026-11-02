package com.rova.banking.account;

import com.rova.banking.domain.*;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.util.*;

@Entity
@Table(name = "bank_account")
public class Account {
  @Id private UUID id;

  @Column(name = "customer_id", nullable = false)
  private UUID customerId;

  @Column(length = 3, nullable = false)
  private String currency;

  @Column(precision = 19, scale = 2, nullable = false)
  private BigDecimal balance;

  @Enumerated(EnumType.STRING)
  @Column(length = 20, nullable = false)
  private Status status;

  @Version private long version;

  protected Account() {}

  public enum Status {
    ACTIVE,
    FROZEN,
    CLOSED
  }

  public static Account open(UUID customerId, String currency) {
    Account account = new Account();
    account.id = UUID.randomUUID();
    account.customerId = Objects.requireNonNull(customerId);
    account.balance = new Money(BigDecimal.ZERO, currency).amount();
    account.currency = currency;
    account.status = Status.ACTIVE;
    return account;
  }

  public void requireActive() {
    if (status != Status.ACTIVE) throw BusinessException.rule("Account is not active");
  }

  public void debit(Money amount) {
    requireActive();
    balance = new Money(balance, currency).subtract(amount).amount();
  }

  public void credit(Money amount) {
    requireActive();
    balance = new Money(balance, currency).add(amount).amount();
  }

  public UUID getId() {
    return id;
  }

  public UUID getCustomerId() {
    return customerId;
  }

  public String getCurrency() {
    return currency;
  }

  public BigDecimal getBalance() {
    return balance;
  }

  public Status getStatus() {
    return status;
  }

  public long getVersion() {
    return version;
  }
}
