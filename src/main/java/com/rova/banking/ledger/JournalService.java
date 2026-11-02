package com.rova.banking.ledger;

import com.rova.banking.domain.*;
import com.rova.banking.payment.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

@Service
public class JournalService {
    private final LedgerRepository ledger;
    private final PostingRepository postings;
    public JournalService(LedgerRepository ledger, PostingRepository postings) {
        this.ledger = ledger;
        this.postings = postings;
    }
    @Transactional(propagation = Propagation.MANDATORY)
    public void record(Posting posting) {
        Money money = Money.positive(posting.getAmount(), posting.getCurrency());
        String debit = posting.getSourceId() != null ? "CUSTOMER:" + posting.getSourceId()
                : posting.getKind() == PostingKind.LOAN_DISBURSEMENT ? "LOAN_RECEIVABLE" : "CASH_CLEARING";
        String credit = posting.getDestinationId() != null ? "CUSTOMER:" + posting.getDestinationId()
                : posting.getKind() == PostingKind.LOAN_REPAYMENT ? "LOAN_RECEIVABLE" : "CASH_CLEARING";
        ledger.append(LedgerEntry.create(posting.getId(), debit, LedgerEntry.Side.DEBIT, money),
                LedgerEntry.create(posting.getId(), credit, LedgerEntry.Side.CREDIT, money));
    }
    @Transactional(readOnly = true, timeout = 5)
    public List<LedgerEntry> ledger(UUID id, int page, int size) {
        if (page < 0 || page > 100000 || size < 1 || size > 100) {
            throw BusinessException.invalid("Page must be 0..100000 and size 1..100");
        }
        postings.find(id).orElseThrow(() -> NotFound.resource("Posting"));
        return ledger.findByPosting(id, page * size, size);
    }
}
