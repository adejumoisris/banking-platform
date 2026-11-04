package com.rova.banking.controller;

import com.rova.banking.dto.AccountStatementView;
import com.rova.banking.dto.TransactionHistoryView;
import com.rova.banking.service.AccountStatementService;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/accounts/{id}")
@PreAuthorize("hasAnyRole('OPERATIONS','AUDITOR')")
public class AccountStatementController {
  private final AccountStatementService statements;

  public AccountStatementController(AccountStatementService statements) {
    this.statements = statements;
  }

  @GetMapping("/transactions")
  public ResponseEntity<TransactionHistoryView> history(
      @PathVariable UUID id,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
          Instant from,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
          Instant to,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "50") int size) {
    return ResponseEntity.ok()
        .cacheControl(CacheControl.noStore())
        .body(TransactionHistoryView.of(statements.history(id, from, to, page, size)));
  }

  @GetMapping("/statement")
  public ResponseEntity<AccountStatementView> statement(
      @PathVariable UUID id,
      @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
      @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "50") int size) {
    return ResponseEntity.ok()
        .cacheControl(CacheControl.noStore())
        .body(AccountStatementView.of(statements.statement(id, from, to, page, size)));
  }

  @GetMapping("/statement.csv")
  public ResponseEntity<byte[]> csv(
      @PathVariable UUID id,
      @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
      @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to) {
    byte[] content = statements.csv(id, from, to).getBytes(StandardCharsets.UTF_8);
    return ResponseEntity.ok()
        .cacheControl(CacheControl.noStore())
        .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
        .header(
            HttpHeaders.CONTENT_DISPOSITION,
            ContentDisposition.attachment()
                .filename("account-" + id + "-statement.csv")
                .build()
                .toString())
        .body(content);
  }
}
