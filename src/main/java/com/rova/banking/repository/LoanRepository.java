package com.rova.banking.repository;

import com.rova.banking.model.Loan;
import java.util.*;

public interface LoanRepository {
  Optional<Loan> find(UUID id);

  Optional<Loan> lock(UUID id);

  Loan save(Loan loan);
}
