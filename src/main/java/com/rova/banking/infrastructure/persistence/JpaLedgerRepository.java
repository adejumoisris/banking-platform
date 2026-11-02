package com.rova.banking.infrastructure.persistence;

import com.rova.banking.ledger.*;
import jakarta.persistence.EntityManager;
import java.util.*;
import org.springframework.stereotype.Repository;

@Repository
public class JpaLedgerRepository implements LedgerRepository {
  private final EntityManager entityManager;

  public JpaLedgerRepository(EntityManager entityManager) {
    this.entityManager = entityManager;
  }

  @Override
  public List<LedgerEntry> findByPosting(UUID id, int offset, int limit) {
    return entityManager
        .createQuery(
            "select e from LedgerEntry e where e.postingId = :id order by e.id", LedgerEntry.class)
        .setParameter("id", id)
        .setFirstResult(offset)
        .setMaxResults(limit)
        .getResultList();
  }

  @Override
  public void append(LedgerEntry debit, LedgerEntry credit) {
    entityManager.persist(debit);
    entityManager.persist(credit);
  }
}
