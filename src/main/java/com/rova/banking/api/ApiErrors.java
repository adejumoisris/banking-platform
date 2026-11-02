package com.rova.banking.api;

import com.rova.banking.domain.BusinessException;
import org.springframework.dao.*;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

@RestControllerAdvice
public class ApiErrors extends ResponseEntityExceptionHandler {
  @ExceptionHandler(BusinessException.class)
  ResponseEntity<ProblemDetail> business(BusinessException ex) {
    HttpStatus status =
        switch (ex.getCode()) {
          case INVALID_REQUEST -> HttpStatus.BAD_REQUEST;
          case RESOURCE_NOT_FOUND -> HttpStatus.NOT_FOUND;
          default -> HttpStatus.CONFLICT;
        };
    ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, ex.getMessage());
    problem.setProperty("code", ex.getCode().name());
    return ResponseEntity.status(status).body(problem);
  }

  @ExceptionHandler(DataIntegrityViolationException.class)
  ResponseEntity<ProblemDetail> constraint(DataIntegrityViolationException ex) {
    return ResponseEntity.status(409)
        .body(
            ProblemDetail.forStatusAndDetail(
                HttpStatus.CONFLICT, "Duplicate resource or conflicting operation"));
  }

  @ExceptionHandler(ConcurrencyFailureException.class)
  ResponseEntity<ProblemDetail> concurrent(ConcurrencyFailureException ex) {
    return ResponseEntity.status(409)
        .header("Retry-After", "1")
        .body(
            ProblemDetail.forStatusAndDetail(
                HttpStatus.CONFLICT,
                "Concurrent operation; retry the original request with the same idempotency key"));
  }
}
