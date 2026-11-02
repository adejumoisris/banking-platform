package com.rova.banking.loan;

import com.rova.banking.domain.*;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.util.*;

@Entity
@Table(name = "loan")
public class Loan {
  @Id private UUID id;

  @Column(name = "account_id", nullable = false)
  private UUID accountId;

  @Column(precision = 19, scale = 2, nullable = false)
  private BigDecimal principal;

  @Column(precision = 19, scale = 2, nullable = false)
  private BigDecimal outstanding;

  @Enumerated(EnumType.STRING)
  @Column(length = 20, nullable = false)
  private Status status;

  protected Loan() {}

  public enum Status {
    PENDING,
    ACTIVE,
    CLOSED
  }

  public static Loan originate(UUID accountId, Money principal) {
    Money.positive(principal.amount(), principal.currency());
    Loan loan = new Loan();
    loan.id = UUID.randomUUID();
    loan.accountId = Objects.requireNonNull(accountId);
    loan.principal = principal.amount();
    loan.outstanding = BigDecimal.ZERO.setScale(2);
    loan.status = Status.PENDING;
    return loan;
  }

  public boolean isDisbursed() {
    return status == Status.ACTIVE || status == Status.CLOSED;
  }

  public void activate() {
    if (status != Status.PENDING) throw BusinessException.rule("Loan is not pending");
    outstanding = principal;
    status = Status.ACTIVE;
  }

  public void repay(BigDecimal amount) {
    if (status != Status.ACTIVE || amount.signum() <= 0 || amount.compareTo(outstanding) > 0) {
      throw BusinessException.rule("Repayment exceeds the active loan balance");
    }
    outstanding = outstanding.subtract(amount);
    if (outstanding.signum() == 0) status = Status.CLOSED;
  }

  public UUID getId() {
    return id;
  }

  public UUID getAccountId() {
    return accountId;
  }

  public BigDecimal getPrincipal() {
    return principal;
  }

  public BigDecimal getOutstanding() {
    return outstanding;
  }

  public Status getStatus() {
    return status;
  }
}
