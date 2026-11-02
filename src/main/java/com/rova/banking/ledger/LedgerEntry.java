package com.rova.banking.ledger;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import com.rova.banking.domain.*;

@Entity
@Table(name="ledger_entry")
public class LedgerEntry {
    @Id
    private UUID id;

    @Column(name="posting_id",nullable=false)
    private UUID postingId;

    @Column(name="ledger_account",length=80,nullable=false)
    private String ledgerAccount;

    @Enumerated(EnumType.STRING) @Column(length=6,nullable=false)
    private Side side;

    @Column(precision=19,scale=2,nullable=false)
    private BigDecimal amount;

    @Column(length=3,nullable=false)
    private String currency;

    protected LedgerEntry() {}

    public enum Side { DEBIT, CREDIT }
    public static LedgerEntry create(UUID postingId, String account, Side side, Money money) {
        LedgerEntry entry = new LedgerEntry();
        entry.id = UUID.randomUUID();
        entry.postingId = postingId;
        entry.ledgerAccount = account;
        entry.side = side;
        entry.amount = money.amount();
        entry.currency = money.currency();
        return entry;
    }

    public UUID getId() { return id; }
    public UUID getPostingId() { return postingId; }
    public String getLedgerAccount() { return ledgerAccount; }
    public Side getSide() { return side; }
    public BigDecimal getAmount() { return amount; }
    public String getCurrency() { return currency; }
}
