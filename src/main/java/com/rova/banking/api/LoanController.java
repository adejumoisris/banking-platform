package com.rova.banking.api;

import com.rova.banking.loan.LoanService;
import com.rova.banking.api.Requests.*;
import com.rova.banking.api.Views.*;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/loans")
public class LoanController {
    private final LoanService loans;
    public LoanController(LoanService loans) { this.loans = loans; }
    @PostMapping @ResponseStatus(HttpStatus.CREATED) @PreAuthorize("hasRole('CREDIT')")
    public LoanView originate(@Valid @RequestBody LoanRequest request) {
        return LoanView.of(loans.originate(request.accountId(), request.principal()));
    }
    @PostMapping("/{id}/disburse") @PreAuthorize("hasRole('CREDIT')")
    public LoanView disburse(@PathVariable UUID id) { return LoanView.of(loans.disburse(id)); }
    @PostMapping("/{id}/repayments") @PreAuthorize("hasRole('OPERATIONS')")
    public PostingView repay(@PathVariable UUID id, @RequestHeader("Idempotency-Key") String key,
                             @Valid @RequestBody AmountRequest request) {
        return PostingView.of(loans.repay(id, key, request.amount()));
    }
    @GetMapping("/{id}") @PreAuthorize("hasAnyRole('CREDIT','AUDITOR')")
    public LoanView get(@PathVariable UUID id) { return LoanView.of(loans.loan(id)); }
}
