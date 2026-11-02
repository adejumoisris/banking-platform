package com.rova.banking.infrastructure.persistence;

import com.rova.banking.loan.*;
import jakarta.persistence.*;
import java.util.*;
import org.springframework.stereotype.Repository;

@Repository
public class JpaLoanRepository implements LoanRepository {
  private final EntityManager entityManager;

  public JpaLoanRepository(EntityManager entityManager) {
    this.entityManager = entityManager;
  }

  @Override
  public Optional<Loan> find(UUID id) {
    return Optional.ofNullable(entityManager.find(Loan.class, id));
  }

  @Override
  public Optional<Loan> lock(UUID id) {
    entityManager.flush();
    Loan entity = entityManager.find(Loan.class, id);
    if (entity != null) {
      // Refresh even if already loaded: a previous unlocked read must not leave a stale balance.
      entityManager.refresh(
          entity, LockModeType.PESSIMISTIC_WRITE, Map.of("jakarta.persistence.lock.timeout", 5000));
    }
    return Optional.ofNullable(entity);
  }

  @Override
  public Loan save(Loan entity) {
    if (!entityManager.contains(entity)) entityManager.persist(entity);
    return entity;
  }
}
