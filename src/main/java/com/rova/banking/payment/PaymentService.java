package com.rova.banking.payment;

import com.rova.banking.account.*;
import com.rova.banking.domain.*;
import com.rova.banking.ledger.JournalService;
import java.math.BigDecimal;
import java.time.Clock;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(timeout = 15)
public class PaymentService {
    private final AccountRepository accounts;
    private final PostingRepository postings;
    private final JournalService journal;
    private final Clock clock;
    public PaymentService(AccountRepository accounts, PostingRepository postings, JournalService journal, Clock clock) {
        this.accounts = accounts;
        this.postings = postings;
        this.journal = journal;
        this.clock = clock;
    }
    public Posting post(String key, UUID source, UUID destination, BigDecimal amount, PostingKind kind) {
        IdempotencyKeys.validate(key, 100);
        Objects.requireNonNull(kind).validateAccounts(source, destination);
        Map<UUID, Account> locked = new HashMap<>();
        List<UUID> ids = new ArrayList<>();
        if (source != null) ids.add(source);
        if (destination != null) ids.add(destination);
        ids.sort(Comparator.naturalOrder());
        for (UUID id : ids) {
            locked.put(id, accounts.lock(id).orElseThrow(() -> NotFound.resource("Account")));
        }
        Account from = locked.get(source);
        Account to = locked.get(destination);
        Money money = Money.positive(amount, from != null ? from.getCurrency() : to.getCurrency());
        Optional<Posting> existing = postings.findByKey(key);
        if (existing.isPresent()) {
            existing.get().requireSameRequest(source, destination, money.amount(), kind);
            return existing.get();
        }
        locked.values().forEach(Account::requireActive);
        if (from != null) from.debit(money);
        if (to != null) to.credit(money);
        if (from != null) accounts.save(from);
        if (to != null) accounts.save(to);
        Posting posting = postings.save(Posting.create(key, source, destination, money, kind, clock.instant()));
        journal.record(posting);
        return posting;
    }
    @Transactional(readOnly = true, timeout = 5)
    public Optional<Posting> findByKey(String key) { return postings.findByKey(key); }
}
