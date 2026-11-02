package com.rova.banking.domain;

import java.util.UUID;

public enum PostingKind {
  DEPOSIT,
  WITHDRAWAL,
  TRANSFER,
  LOAN_DISBURSEMENT,
  LOAN_REPAYMENT;

  public void validateAccounts(UUID source, UUID destination) {
    boolean valid =
        switch (this) {
          case DEPOSIT, LOAN_DISBURSEMENT -> source == null && destination != null;
          case WITHDRAWAL, LOAN_REPAYMENT -> source != null && destination == null;
          case TRANSFER -> source != null && destination != null && !source.equals(destination);
        };
    if (!valid) throw BusinessException.invalid("Posting accounts do not match the operation");
  }
}
