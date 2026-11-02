package com.rova.banking.loan;

import com.rova.banking.audit.ActivityRecorder;
import com.rova.banking.account.*;
import com.rova.banking.domain.*;
import com.rova.banking.payment.*;
import java.math.BigDecimal;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(timeout = 15)
public class LoanService {
    private final ActivityRecorder activity;
    private final LoanRepository loans;
    private final AccountRepository accounts;
    private final PaymentService payments;
    public LoanService(LoanRepository loans, AccountRepository accounts, PaymentService payments, ActivityRecorder activity) {
        this.activity = activity;
        this.loans = loans;
        this.accounts = accounts;
        this.payments = payments;
    }
    public Loan originate(UUID accountId, BigDecimal principal) {
        Account account = accounts.lock(accountId).orElseThrow(() -> NotFound.resource("Account"));
        account.requireActive();
        Loan loan = loans.save(Loan.originate(accountId, Money.positive(principal, account.getCurrency())));
        activity.record("loan.originated", loan.getId());
        return loan;
    }
    public Loan disburse(UUID id) {
        Loan loan = loans.lock(id).orElseThrow(() -> NotFound.resource("Loan"));
        if (loan.isDisbursed()) return loan;
        payments.post("loan-disbursement:" + id, null, loan.getAccountId(), loan.getPrincipal(), PostingKind.LOAN_DISBURSEMENT);
        loan.activate();
        activity.record("loan.disbursed", id);
        return loans.save(loan);
    }
    public Posting repay(UUID id, String key, BigDecimal amount) {
        IdempotencyKeys.validate(key, 50);
        Loan loan = loans.lock(id).orElseThrow(() -> NotFound.resource("Loan"));
        String scoped = "repay:" + id + ":" + key;
        var existing = payments.findByKey(scoped);
        if (existing.isPresent()) {
            existing.get().requireSameRequest(loan.getAccountId(), null, amount, PostingKind.LOAN_REPAYMENT);
            return existing.get();
        }
        Account account = accounts.lock(loan.getAccountId()).orElseThrow(() -> NotFound.resource("Account"));
        Money repayment = Money.positive(amount, account.getCurrency());
        loan.repay(repayment.amount());
        Posting posting = payments.post(scoped, loan.getAccountId(), null, repayment.amount(), PostingKind.LOAN_REPAYMENT);
        loans.save(loan);
        activity.record("loan.repaid", id);
        return posting;
    }
    @Transactional(readOnly = true, timeout = 5)
    public Loan loan(UUID id) { return loans.find(id).orElseThrow(() -> NotFound.resource("Loan")); }
}
