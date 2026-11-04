package com.rova.banking.infrastructure.persistence;

import com.rova.banking.model.Posting;
import com.rova.banking.repository.PostingRepository;
import jakarta.persistence.EntityManager;
import java.util.*;
import org.springframework.stereotype.Repository;

@Repository
public class JpaPostingRepository implements PostingRepository {
  private final EntityManager entityManager;

  public JpaPostingRepository(EntityManager entityManager) {
    this.entityManager = entityManager;
  }

  @Override
  public Optional<Posting> find(UUID id) {
    return Optional.ofNullable(entityManager.find(Posting.class, id));
  }

  @Override
  public Optional<Posting> findByKey(String key) {
    return entityManager
        .createQuery("select p from Posting p where p.idempotencyKey = :key", Posting.class)
        .setParameter("key", key)
        .getResultStream()
        .findFirst();
  }

  @Override
  public Posting save(Posting posting) {
    entityManager.persist(posting);
    return posting;
  }
}
