package com.rova.banking.controller;

import com.rova.banking.dto.FineractCommandView;
import com.rova.banking.service.FineractService;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/integrations/fineract")
@ConditionalOnProperty(name = "fineract.enabled", havingValue = "true")
public class FineractController {
  private final FineractService service;

  public FineractController(FineractService service) {
    this.service = service;
  }

  @PostMapping("/customers/{id}")
  @ResponseStatus(HttpStatus.ACCEPTED)
  @PreAuthorize("hasRole('OPERATIONS')")
  public FineractCommandView customer(@PathVariable UUID id) {
    return FineractCommandView.of(service.enqueueCustomer(id));
  }

  @PostMapping("/accounts/{id}")
  @ResponseStatus(HttpStatus.ACCEPTED)
  @PreAuthorize("hasRole('OPERATIONS')")
  public FineractCommandView account(@PathVariable UUID id) {
    return FineractCommandView.of(service.enqueueAccount(id));
  }

  @GetMapping("/commands/{id}")
  @PreAuthorize("hasAnyRole('OPERATIONS','AUDITOR')")
  public FineractCommandView command(@PathVariable UUID id) {
    return FineractCommandView.of(service.command(id));
  }

  @PostMapping("/commands/{id}/dispatch")
  @PreAuthorize("hasRole('OPERATIONS')")
  public FineractCommandView dispatch(@PathVariable UUID id) {
    return FineractCommandView.of(service.dispatch(id));
  }

  @PostMapping("/commands/{id}/reconcile")
  @PreAuthorize("hasRole('OPERATIONS')")
  public FineractCommandView reconcile(@PathVariable UUID id) {
    return FineractCommandView.of(service.reconcile(id));
  }

  @GetMapping("/commands")
  @PreAuthorize("hasAnyRole('OPERATIONS','AUDITOR')")
  public org.springframework.http.ResponseEntity<
          com.rova.banking.model.PageResult<FineractCommandView>>
      commands(
          @RequestParam(defaultValue = "") String q,
          @RequestParam(required = false) com.rova.banking.model.FineractCommand.Status status,
          @RequestParam(defaultValue = "0") int page,
          @RequestParam(defaultValue = "25") int size) {
    var result = service.commands(q, status, page, size);
    return org.springframework.http.ResponseEntity.ok()
        .cacheControl(org.springframework.http.CacheControl.noStore())
        .body(
            new com.rova.banking.model.PageResult<>(
                result.items().stream().map(FineractCommandView::of).toList(),
                result.page(),
                result.size(),
                result.totalElements()));
  }
}
