package com.rova.banking;
import com.rova.banking.Models.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.math.BigDecimal;
import java.util.*;
@RestController @RequestMapping("/api/v1")
public class BankingController {
 private final BankingService service;
 public BankingController(BankingService service) { this.service=service; }
 public record CustomerRequest(@NotBlank @Size(max=160) String fullName,@NotBlank @Email @Size(max=254) String email) {}
 public record AccountRequest(@NotNull UUID customerId,@NotNull @Pattern(regexp="NGN|USD|GBP|EUR") String currency) {}
 public record AmountRequest(@NotNull @DecimalMin("0.01") @Digits(integer=17,fraction=2) BigDecimal amount) {}
 public record TransferRequest(@NotNull UUID sourceId,@NotNull UUID destinationId,@NotNull @DecimalMin("0.01") @Digits(integer=17,fraction=2) BigDecimal amount) {}
 public record LoanRequest(@NotNull UUID accountId,@NotNull @DecimalMin("0.01") @Digits(integer=17,fraction=2) BigDecimal principal) {}
 @PostMapping("/customers") @PreAuthorize("hasRole('OPERATIONS')")
 public Customer customer(@Valid @RequestBody CustomerRequest r) { return service.createCustomer(r.fullName(),r.email()); }
 @PostMapping("/customers/{id}/verify") @PreAuthorize("hasRole('COMPLIANCE')")
 public Customer verify(@PathVariable UUID id) { return service.verifyCustomer(id); }
 @PostMapping("/accounts") @PreAuthorize("hasRole('OPERATIONS')")
 public Account open(@Valid @RequestBody AccountRequest r) { return service.openAccount(r.customerId(),r.currency()); }
 @GetMapping("/accounts/{id}") @PreAuthorize("hasAnyRole('OPERATIONS','AUDITOR')")
 public Account account(@PathVariable UUID id) { return service.account(id); }
 @PostMapping("/accounts/{id}/deposits") @PreAuthorize("hasRole('TELLER')")
 public Posting deposit(@PathVariable UUID id,@RequestHeader("Idempotency-Key") String key,@Valid @RequestBody AmountRequest r) { return service.post(publicKey(key),null,id,r.amount(),"DEPOSIT"); }
 @PostMapping("/accounts/{id}/withdrawals") @PreAuthorize("hasRole('TELLER')")
 public Posting withdraw(@PathVariable UUID id,@RequestHeader("Idempotency-Key") String key,@Valid @RequestBody AmountRequest r) { return service.post(publicKey(key),id,null,r.amount(),"WITHDRAWAL"); }
 @PostMapping("/transfers") @PreAuthorize("hasRole('OPERATIONS')")
 public Posting transfer(@RequestHeader("Idempotency-Key") String key,@Valid @RequestBody TransferRequest r) { return service.post(publicKey(key),r.sourceId(),r.destinationId(),r.amount(),"TRANSFER"); }
 @GetMapping("/postings/{id}/ledger") @PreAuthorize("hasRole('AUDITOR')")
 public List<LedgerEntry> ledger(@PathVariable UUID id) { return service.ledger(id); }
 @PostMapping("/loans") @PreAuthorize("hasRole('CREDIT')")
 public Loan loan(@Valid @RequestBody LoanRequest r) { return service.originate(r.accountId(),r.principal()); }
 @PostMapping("/loans/{id}/disburse") @PreAuthorize("hasRole('CREDIT')")
 public Loan disburse(@PathVariable UUID id) { return service.disburse(id); }
 @PostMapping("/loans/{id}/repayments") @PreAuthorize("hasRole('OPERATIONS')")
 public Posting repay(@PathVariable UUID id,@RequestHeader("Idempotency-Key") String key,@Valid @RequestBody AmountRequest r) { return service.repay(id,key,r.amount()); }
 private String publicKey(String key) {
  if(key.startsWith("loan-disbursement:") || key.startsWith("repay:"))
   throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST,"Reserved idempotency key prefix");
  return key;
 }
}
