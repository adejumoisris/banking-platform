package com.rova.banking.infrastructure.persistence;

import com.rova.banking.customer.*;
import jakarta.persistence.*;
import java.util.*;
import org.springframework.stereotype.Repository;

@Repository
public class JpaCustomerRepository implements CustomerRepository {
  private final EntityManager entityManager;

  public JpaCustomerRepository(EntityManager entityManager) {
    this.entityManager = entityManager;
  }

  @Override
  public Optional<Customer> find(UUID id) {
    return Optional.ofNullable(entityManager.find(Customer.class, id));
  }

  @Override
  public Optional<Customer> lock(UUID id) {
    entityManager.flush();
    Customer entity = entityManager.find(Customer.class, id);
    if (entity != null) {
      // Refresh even if already loaded: a previous unlocked read must not leave a stale balance.
      entityManager.refresh(
          entity, LockModeType.PESSIMISTIC_WRITE, Map.of("jakarta.persistence.lock.timeout", 5000));
    }
    return Optional.ofNullable(entity);
  }

  @Override
  public Customer save(Customer entity) {
    if (!entityManager.contains(entity)) entityManager.persist(entity);
    return entity;
  }
}
