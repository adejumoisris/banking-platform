package com.rova.banking.infrastructure.persistence;

import com.rova.banking.account.*;
import jakarta.persistence.*;
import java.util.*;
import org.springframework.stereotype.Repository;

@Repository
public class JpaAccountRepository implements AccountRepository {
    private final EntityManager entityManager;

    public JpaAccountRepository(EntityManager entityManager) { this.entityManager = entityManager; }

    @Override public Optional<Account> find(UUID id) {
        return Optional.ofNullable(entityManager.find(Account.class, id));
    }

    @Override public Optional<Account> lock(UUID id) {
        entityManager.flush();
        Account entity = entityManager.find(Account.class, id);
        if (entity != null) {
            // Refresh even if already loaded: a previous unlocked read must not leave a stale balance.
            entityManager.refresh(entity, LockModeType.PESSIMISTIC_WRITE,
                    Map.of("jakarta.persistence.lock.timeout", 5000));
        }
        return Optional.ofNullable(entity);
    }

    @Override public Account save(Account entity) {
        if (!entityManager.contains(entity)) entityManager.persist(entity);
        return entity;
    }
}
