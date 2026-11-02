package com.rova.banking.loan;

import java.util.*;

public interface LoanRepository {
  Optional<Loan> find(UUID id);

  Optional<Loan> lock(UUID id);

  Loan save(Loan loan);
}
