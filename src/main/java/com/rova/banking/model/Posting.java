package com.rova.banking.model;

import com.rova.banking.domain.*;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;

@Entity
@Table(name = "posting")
public class Posting {
  @Id private UUID id;

  @Column(name = "idempotency_key", length = 100, nullable = false)
  private String idempotencyKey;

  @Column(name = "source_id")
  private UUID sourceId;

  @Column(name = "destination_id")
  private UUID destinationId;

  @Column(precision = 19, scale = 2, nullable = false)
  private BigDecimal amount;

  @Column(length = 3, nullable = false)
  private String currency;

  @Enumerated(EnumType.STRING)
  @Column(length = 30, nullable = false)
  private PostingKind kind;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  protected Posting() {}

  public static Posting create(
      String key, UUID source, UUID destination, Money money, PostingKind kind, Instant now) {
    Money.positive(money.amount(), money.currency());
    kind.validateAccounts(source, destination);
    Posting posting = new Posting();
    posting.id = UUID.randomUUID();
    posting.idempotencyKey = key;
    posting.sourceId = source;
    posting.destinationId = destination;
    posting.amount = money.amount();
    posting.currency = money.currency();
    posting.kind = kind;
    posting.createdAt = now;
    return posting;
  }

  public void requireSameRequest(
      UUID source, UUID destination, BigDecimal requestedAmount, PostingKind requestedKind) {
    if (!Objects.equals(sourceId, source)
        || !Objects.equals(destinationId, destination)
        || requestedAmount == null
        || amount.compareTo(requestedAmount) != 0
        || kind != requestedKind) {
      throw new BusinessException(
          BusinessException.Code.IDEMPOTENCY_CONFLICT,
          "Idempotency key was used for a different request");
    }
  }

  public UUID getId() {
    return id;
  }

  public String getIdempotencyKey() {
    return idempotencyKey;
  }

  public UUID getSourceId() {
    return sourceId;
  }

  public UUID getDestinationId() {
    return destinationId;
  }

  public BigDecimal getAmount() {
    return amount;
  }

  public String getCurrency() {
    return currency;
  }

  public PostingKind getKind() {
    return kind;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }
}
