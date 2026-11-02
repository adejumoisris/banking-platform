package com.rova.banking;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.*;
import org.springframework.dao.DataIntegrityViolationException;
@RestControllerAdvice
public class ApiErrors {
 @ExceptionHandler(org.springframework.web.server.ResponseStatusException.class)
 ResponseEntity<ProblemDetail> business(org.springframework.web.server.ResponseStatusException ex) {
  return ResponseEntity.status(ex.getStatusCode()).body(ProblemDetail.forStatusAndDetail(ex.getStatusCode(),ex.getReason()==null?"Request rejected":ex.getReason()));
 }
 @ExceptionHandler(DataIntegrityViolationException.class)
 ResponseEntity<ProblemDetail> conflict(DataIntegrityViolationException ex) {
  return ResponseEntity.status(409).body(ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT,"Duplicate resource or conflicting concurrent operation; retry with the same idempotency key"));
 }
}
