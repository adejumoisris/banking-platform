package com.rova.banking.model;

import com.rova.banking.domain.PostingKind;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** One customer-account leg of a posted journal. Internal idempotency keys are never exposed. */
public record AccountTransaction(
    UUID reference,
    Instant occurredAt,
    PostingKind type,
    UUID counterpartyAccountId,
    BigDecimal moneyIn,
    BigDecimal moneyOut) {}
